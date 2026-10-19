package FileExplorerCollation.filesearch.config;

/**
 * Locations, file names and run settings used by the ITR (income tax return) collation.
 *
 * Kept apart from {@link CollationPaths} so nothing the BAS collation reads can be
 * changed by an ITR edit.
 */
public final class ItrPaths {

    /** Tax year being collated - the year folder and the "2026_ITR_" file prefix. */
    public static final String ITR_YEAR = setting("ITR_YEAR", "2026");

    /** Marker between the year and the client name in "2026_ITR_ClientName". */
    public static final String ITR_FILE_MARKER = "ITR";

    /** Folder holding the "Notes for Persons Signing Tax Returns" hand-out. */
    public static final String ITR_TEMPLATE_FOLDER =
            "\\\\fabafs01\\Accounting Perth\\FABA\\Admin\\Ramandeep Kaur\\Collation Templates\\Individuals";

    /** Copied into every ITR pack unchanged. */
    public static final String NOTES_FOR_SIGNING_FILE = "Notes for Persons Signing Tax Returns.pdf";

    /**
     * The ITR letters folder in Downloads: the Word cover letter template (any .docx
     * with "Cover Letter" in its name) and the engagement letter.
     */
    public static final String ITR_LETTERS_FOLDER =
            System.getProperty("user.home") + "\\Downloads\\ITR Letters";

    /**
     * Off until the practice settles when an engagement letter is due (scope 3.5).
     * The filling-in code is kept; set this to true, or ITR_ATTACH_ENGAGEMENT_LETTER=true,
     * to put it back to work for jobs with no Ignition link.
     */
    public static final boolean ATTACH_ENGAGEMENT_LETTER =
            Boolean.parseBoolean(setting("ITR_ATTACH_ENGAGEMENT_LETTER", "false"));

    /** Copied into Collation Testing when the job's Description has no Ignition link. */
    public static final String ENGAGEMENT_LETTER_FILE = "New Engagement Letter 2026-27.docx";

    /** The Fee From Refund authority, in the ITR Letters folder (scope 3.6). */
    public static final String FFR_TEMPLATE_FILE = "Fee From Refund (new authority).docx";

    /** What the finished ITR letter is called, e.g. "Cover Letter 2026.docx". */
    public static final String COVER_LETTER_FILE_NAME = "Cover Letter " + ITR_YEAR + ".docx";

    private ItrPaths() {
        // constants only
    }

    /** A JVM system property, then an environment variable of the same name, then the default. */
    private static String setting(String name, String fallback) {

        String value = System.getProperty(name);

        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(name);
        }

        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
