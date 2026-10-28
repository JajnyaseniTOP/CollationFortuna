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

    /** S - the client's Business structure on XPM, e.g. "Individual". Written on a job's first row. */
    public static final int BUSINESS_STRUCTURE = 18;

    /** T - "BAS" or "ITR": which collation the job got. */
    public static final int COLLATION_TYPE = 19;

    /** U - ITR jobs: the client's Preferred Name on XPM. */
    public static final int PREFERRED_NAME = 20;

    /** V - ITR jobs: who the cover letter is addressed to, from the ITR declaration. */
    public static final int COVER_LETTER_RECIPIENT = 21;

    /** W - ITR jobs: the tax return's due date from the ATO. */
    public static final int ATO_DUE_DATE = 22;

    /** X - ITR jobs: final invoice number(s) from the job's Financial tab. */
    public static final int FINAL_INVOICE_NO = 23;

    /** Y - ITR jobs: the final invoice amount(s). */
    public static final int FINAL_INVOICE_AMOUNT = 24;

    /** Z - ITR jobs: "Yes" when a Fee From Refund authority was prepared. */
    public static final int FFR_PREPARED = 25;

    /** AA - ITR jobs: the FFR's expected balance, refund minus final invoice. */
    public static final int FFR_EXPECTED_BALANCE = 26;

    /**
     * AB to AG - Trust ITR jobs, one row per entity: Entities Included, Entity,
     * Financial Statement, Directors, Ignition Job, Engagement Letter (see ClientExcel).
     */
    public static final int ENTITIES_INCLUDED = 27;

    /**
     * AO - written by the legacy folder search: whether the client folder was located.
     * Kept well clear of the columns the collation writes.
     */
    public static final int CLIENT_FOLDER_STATUS = 40;

    /** Header text written above {@link #CLIENT_FOLDER_STATUS}. */
    public static final String CLIENT_FOLDER_STATUS_HEADER = "Client Folder Status";

    /** AP - written by the legacy folder search: whether the AS file was located. */
    public static final int AS_FILE_STATUS = 41;

    /** Header text written above {@link #AS_FILE_STATUS}. */
    public static final String AS_FILE_STATUS_HEADER = "AS File Status";

    public static final String STATUS_FOUND = "Found";

    public static final String STATUS_NOT_FOUND = "Not Found";

    private ClientDataColumns() {
        // constants only
    }
}
