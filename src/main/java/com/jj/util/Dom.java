package com.jj.util;

/**
 * JavaScript for reading pages that Xero and the ATO build out of web components.
 *
 * <p>Both sites render through custom elements, which defeats ordinary automation in
 * two separate ways:
 *
 * <ul>
 *   <li><b>Shadow roots</b> — {@code textContent} and Selenium's {@code getText()}
 *       stop at the boundary, so a component's contents read as an empty string.</li>
 *   <li><b>Slots</b> — a component renders content that still lives in the host's
 *       light DOM. Walking a slot's own children finds nothing; only
 *       {@code assignedNodes()} reaches the values.</li>
 * </ul>
 *
 * <p>Both caught this project out. The ATO's Tax registrations table read as empty
 * for a client that plainly had registrations, and the lodgement cycle silently fell
 * back to monthly — which would have collated a quarterly client as twelve months.
 * Anything reading text from these sites should use {@link #DEEP_TEXT} rather than
 * textContent.
 */
public final class Dom {

	/**
	 * Defines {@code deepText(node)}: an element's text including everything inside
	 * its shadow roots and slots. Prepend to any script that needs it.
	 *
	 * <p>Style and script nodes are skipped, or a component's stylesheet swamps the
	 * result.
	 */
	public static final String DEEP_TEXT =
			"function deepText(node){let out='';"
			+ "for(const c of node.childNodes){"
			+ "if(c.nodeType===3){out+=c.nodeValue+' ';}"
			+ "else if(c.nodeType===1){"
			+ "if(c.tagName==='STYLE'||c.tagName==='SCRIPT')continue;"
			+ "if(c.tagName==='SLOT'){"
			+ "for(const a of c.assignedNodes({flatten:true})){"
			+ "if(a.nodeType===3){out+=a.nodeValue+' ';}"
			+ "else if(a.nodeType===1){if(a.shadowRoot)out+=deepText(a.shadowRoot);out+=deepText(a);}}"
			+ "continue;}"
			+ "if(c.shadowRoot)out+=deepText(c.shadowRoot);"
			+ "out+=deepText(c);}}"
			+ "return out;}";

	/**
	 * Defines {@code eachShadowRoot(fn)}: runs {@code fn(root)} for the document and
	 * every shadow root beneath it, however deeply nested.
	 */
	public static final String WALK_SHADOW =
			"function eachShadowRoot(fn){"
			+ "function walk(root){fn(root);"
			+ "for(const el of root.querySelectorAll('*'))if(el.shadowRoot)walk(el.shadowRoot);}"
			+ "walk(document);}";

	/** Selector matching a table row in either plain HTML or the ATO's components. */
	public static final String ROW_SELECTOR = "[role='row'],tr,ng-com-table-row,com-table-tr";

	private Dom() {
		// constants only
	}
}
