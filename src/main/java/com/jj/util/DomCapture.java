package com.jj.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Writes a page's markup to disk so locators can be rebuilt against what the site
 * actually renders, rather than guessed.
 *
 * <p>Each capture produces two files:
 *
 * <ul>
 *   <li><b>.candidates.txt</b> — one line per element worth targeting, with its
 *       data-automationid, id, aria-label, role, text and class, plus a suggested
 *       XPath. This is the file to read first.</li>
 *   <li><b>.html</b> — the full DOM, with every shadow root dumped after it, for
 *       when the summary is not enough.</li>
 * </ul>
 *
 * <p>Both files contain whatever was on screen, so a capture of a real client page
 * holds real client data. Check before sharing them.
 */
public class DomCapture {

	private static final DateTimeFormatter FOLDER_STAMP =
			DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	/** Stops a table-heavy page producing a summary nobody can read. */
	private static final int MAX_CANDIDATES = 1500;

	private static final String CANDIDATES_JS =
			"const clean=v=>(v||'').replace(/\\s+/g,' ').trim();"
			+ "const attr=(el,n)=>{try{return el.getAttribute(n)||'';}catch(e){return '';}};"
			// td/dt/dd earn their place: label-and-value pairs are how the job and
			// client pages expose Job No., Cover Letter Notes, Client and Internal Team.
			+ "const TAGS=['a','button','input','select','textarea','label','th','td','dt','dd',"
			+ "'h1','h2','h3','summary'];"
			+ "const lines=[];let index=0;let truncated=false;"
			+ "function suggest(el,shadow){"
			+ "const tag=el.tagName.toLowerCase();"
			+ "if(shadow)return '=> inside shadow DOM, reach it with JS rather than XPath';"
			+ "const aid=attr(el,'data-automationid');"
			+ "if(aid)return \"=> //\"+tag+\"[@data-automationid='\"+aid+\"']\";"
			+ "const id=attr(el,'id');"
			+ "if(id&&!/\\d{4,}/.test(id))return \"=> //\"+tag+\"[@id='\"+id+\"']\";"
			+ "const label=attr(el,'aria-label');"
			+ "if(label)return \"=> //\"+tag+\"[@aria-label='\"+label+\"']\";"
			+ "const placeholder=attr(el,'placeholder');"
			+ "if(placeholder)return \"=> //\"+tag+\"[@placeholder='\"+placeholder+\"']\";"
			+ "const text=clean(el.textContent);"
			+ "if(text&&text.length<=40)return \"=> //\"+tag+\"[normalize-space()='\"+text+\"']\";"
			+ "return '';"
			+ "}"
			+ "function candidate(el){"
			+ "const tag=el.tagName.toLowerCase();"
			+ "if(TAGS.indexOf(tag)>=0)return true;"
			+ "return !!(attr(el,'data-automationid')||attr(el,'aria-label')||attr(el,'role')"
			+ "||attr(el,'placeholder')||attr(el,'name'));"
			+ "}"
			+ "function visible(el){try{return el.getClientRects().length>0;}catch(e){return false;}}"
			+ "function walk(root,shadow){"
			+ "for(const el of root.querySelectorAll('*')){"
			+ "if(el.shadowRoot)walk(el.shadowRoot,true);"
			+ "if(lines.length>=" + MAX_CANDIDATES + "){truncated=true;return;}"
			+ "if(!candidate(el))continue;"
			+ "const parts=['['+(index++)+']','<'+el.tagName.toLowerCase()+'>'];"
			+ "if(shadow)parts.push('SHADOW');"
			+ "if(!visible(el))parts.push('hidden');"
			+ "const named=[['data-automationid','aid'],['id','id'],['aria-label','aria'],"
			+ "['role','role'],['name','name'],['placeholder','placeholder'],['type','type']];"
			+ "for(const pair of named){const v=attr(el,pair[0]);"
			// 120, not 60: ids like the ATO's client search run past 60 characters, and
			// a truncated value reads as a different id than the one in the DOM.
			+ "if(v)parts.push(pair[1]+'=\"'+clean(v).slice(0,120)+'\"');}"
			+ "const text=clean(el.textContent).slice(0,80);"
			+ "if(text)parts.push('text=\"'+text+'\"');"
			+ "const cls=clean(attr(el,'class'));"
			+ "if(cls)parts.push('class=\"'+cls.slice(0,90)+'\"');"
			+ "const hint=suggest(el,shadow);"
			+ "if(hint)parts.push(hint);"
			+ "lines.push(parts.join(' '));"
			+ "}"
			+ "}"
			+ "walk(document,false);"
			+ "if(truncated)lines.push('... truncated at " + MAX_CANDIDATES + " elements');"
			+ "return lines.join('\\n');";

	private static final String HTML_JS =
			"const parts=['<!-- MAIN DOCUMENT -->',document.documentElement.outerHTML];"
			+ "let count=0;"
			+ "function walk(root){"
			+ "for(const el of root.querySelectorAll('*')){"
			+ "if(!el.shadowRoot)continue;"
			+ "count++;"
			+ "const aid=el.getAttribute('data-automationid');"
			+ "parts.push('\\n<!-- SHADOW ROOT #'+count+' host <'+el.tagName.toLowerCase()"
			+ "+(el.id?' id=\"'+el.id+'\"':'')"
			+ "+(aid?' data-automationid=\"'+aid+'\"':'')+'> -->');"
			+ "parts.push(el.shadowRoot.innerHTML);"
			+ "walk(el.shadowRoot);"
			+ "}"
			+ "}"
			+ "walk(document);"
			+ "return parts.join('\\n');";

	private final Path folder;
	private int sequence = 0;

	public DomCapture() {
		this(Paths.get(System.getProperty("user.home"), "Downloads", "DOM Captures",
				LocalDateTime.now().format(FOLDER_STAMP)));
	}

	public DomCapture(Path folder) {
		this.folder = folder;
	}

	public Path getFolder() {
		return folder;
	}

	public int getCaptureCount() {
		return sequence;
	}

	/**
	 * Writes the current page to the capture folder.
	 *
	 * @param label short name for the screen, e.g. "job-page"
	 */
	public void capture(WebDriver driver, String label) {

		sequence++;

		String fileStem = String.format("%02d_%s", sequence, safeName(label));

		try {
			Files.createDirectories(folder);

			JavascriptExecutor js = (JavascriptExecutor) driver;

			String header = "URL      : " + driver.getCurrentUrl() + System.lineSeparator()
					+ "Title    : " + driver.getTitle() + System.lineSeparator()
					+ "Captured : " + LocalDateTime.now() + System.lineSeparator()
					+ "----------------------------------------------------------" + System.lineSeparator();

			// Both dumps come from a single script call. Run as two calls, an async
			// re-render between them can leave the summary and the markup describing
			// different states of the page.
			List<?> dumps = (List<?>) js.executeScript(
					"return [(function(){" + CANDIDATES_JS + "})(),(function(){" + HTML_JS + "})()];");

			write(folder.resolve(fileStem + ".candidates.txt"), header + String.valueOf(dumps.get(0)));
			write(folder.resolve(fileStem + ".html"), String.valueOf(dumps.get(1)));

			System.out.println("  captured [" + sequence + "] " + label + "  ->  " + fileStem + ".*");

		} catch (Exception e) {
			System.out.println("  could not capture '" + label + "': " + e.getMessage());
		}
	}

	private void write(Path file, String content) throws IOException {
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
	}

	/** Turns a page title or URL fragment into something that can be a file name. */
	static String safeName(String label) {

		if (label == null || label.trim().isEmpty()) {
			return "page";
		}

		String cleaned = label.trim()
				.replaceAll("[\\\\/:*?\"<>|]", "-")
				.replaceAll("\\s+", "-")
				.replaceAll("-{2,}", "-");

		return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
	}
}
