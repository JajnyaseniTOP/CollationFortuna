package FileExplorerCollation.filesearch.config;

/**
 * All hard-coded network locations and file names used by the collation process.
 * Keeping them in one place means a path change is a one-line edit here.
 */
public final class CollationPaths {

    /** Folder that holds the exported ClientData.xls. */
    public static final String DOWNLOADS_FOLDER = System.getProperty("user.home") + "\\Downloads";

    /** Excel file that drives the whole run. */
    public static final String CLIENT_DATA_FILE = DOWNLOADS_FOLDER + "\\ClientData.xls";

    /** Client root used when the job number starts with "KPF". */
    public static final String FUT_CLIENTS = "\\\\fabafs01\\Accounting Perth\\FUT\\Clients";

    /** Client root used when the job number starts with "J". */
    public static final String FABA_CLIENTS = "\\\\fabafs01\\Accounting Perth\\FABA\\Clients";

    /** Job number prefix that routes to {@link #FUT_CLIENTS}. */
    public static final String JOB_PREFIX_FUT = "KPF";

    /**
     * Job number prefix that routes to {@link #FABA_CLIENTS}.
     * Just "J" on purpose: real job numbers read "J010726" (J + zero), not "JO".
     */
    public static final String JOB_PREFIX_FABA = "J";

    /** Folder holding the BAS collation templates. */
    public static final String BAS_TEMPLATE_FOLDER =
            "\\\\fabafs01\\Accounting Perth\\FABA\\Admin\\Ramandeep Kaur\\Collation Templates\\BAS Collation\\2026";

    /** Cover letter copied next to the quarter file. */
    public static final String BAS_COVER_LETTER_FILE = "BAS Cover Letter Fortuna 2026.docx";

    /** Folder created inside the quarter folder to hold the collated files. */
    public static final String COLLATION_FOLDER_NAME = "Collation Testing";

    private CollationPaths() {
        // constants only
    }
}
