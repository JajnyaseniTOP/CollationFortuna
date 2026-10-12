package FileExplorerCollation.filesearch.config;

/**
 * Column positions inside ClientData.xls (sheet "Client Data").
 *
 * The sheet is produced by the Fortunacollation project, so if a column is added
 * or moved there, this is the only place that needs updating here.
 */
public final class ClientDataColumns {

    /** A - job number, e.g. "J010726" or "KPF...". */
    public static final int JOB_NO = 0;

    /** B - lodgement cycle worked out from the ATO: "Monthly" or a quarter. */
    public static final int LODGEMENT_CYCLE = 1;

    /** C - free text notes for the cover letter. */
    public static final int COVER_LETTER_NOTES = 2;

    /** E - client name, used to build the quarter file name. */
    public static final int CLIENT_NAME = 4;

    /** G - client code, e.g. "HOBBCP01". */
    public static final int CLIENT_CODE = 6;

    /** I - internal team, e.g. "A". Appended to the names in K/L/M. */
    public static final int INTERNAL_TEAM = 8;

    /** J - "Group+TeamName", the top level folder under Clients. */
    public static final int GROUP_TEAM_NAME = 9;

    /** K - first client folder name to try: "Name (CODE) - Team". */
    public static final int CLIENT_FOLDER_NAME_K = 10;

    /** L - second client folder name to try: "Name - Team". */
    public static final int CLIENT_FOLDER_NAME_L = 11;

    /** M - third client folder name to try: reversed name - Team. */
    public static final int CLIENT_FOLDER_NAME_M = 12;

    /** N - the month this row covers, e.g. "Apr 2026". One row per month of the BAS period. */
    public static final int MONTH = 13;

    /** O - whether the ATO had a lodgement for this month. */
    public static final int ATO_STATUS = 14;

    /** P - "Amount payable" or the refund wording. */
    public static final int PAYABLE_OR_REFUND = 15;

    /** Q - the amount for the cover letter's estimated tax position. */
    public static final int AMOUNT = 16;

    /** R - financial quarter, e.g. "Q1 2027". Its year picks the year folder. */
    public static final int FINANCIAL_QUARTER = 17;

    /** S - written by this project: whether the client folder was located. */
    public static final int CLIENT_FOLDER_STATUS = 18;

    /** Header text written above {@link #CLIENT_FOLDER_STATUS}. */
    public static final String CLIENT_FOLDER_STATUS_HEADER = "Client Folder Status";

    /** T - written by this project: whether the AS file was located. */
    public static final int AS_FILE_STATUS = 19;

    /** Header text written above {@link #AS_FILE_STATUS}. */
    public static final String AS_FILE_STATUS_HEADER = "AS File Status";

    public static final String STATUS_FOUND = "Found";

    public static final String STATUS_NOT_FOUND = "Not Found";

    private ClientDataColumns() {
        // constants only
    }
}
