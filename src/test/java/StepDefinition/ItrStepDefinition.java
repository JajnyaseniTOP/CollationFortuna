package StepDefinition;

import Driver_manager.DriverManager;
import Pages.ATOLoginPage;
import Pages.FindJob;
import com.jj.collation.ItrCollator;
import com.jj.collation.JobType;
import com.jj.util.ClientExcel;
import com.jj.util.MainClass;
import FileExplorerCollation.filesearch.config.ItrPaths;
import FileExplorerCollation.filesearch.document.ItrPositionReader;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import FileExplorerCollation.filesearch.document.EngagementLetterService;

/**
 * The ITR (income tax return) run for one job.
 *
 * <p>Its own steps from start to finish, so the BAS steps in
 * {@link FindJobStepDefinition} and {@link ATOLoginStepDefinition} are not shared
 * with it. Every step here does nothing unless the job's name made it an ITR job.
 *
 * <p>The order differs from BAS: the estimated position is read from the ITR document
 * in the client folder, so the folder search has to come before the ATO leg.
 */
public class ItrStepDefinition {

    private final ItrCollator collator = new ItrCollator();

    private ItrCollator.ItrPack pack;
    private Path paymentSlip;

    /** What the collation managed; null until it has run. */
    private ItrCollator.ItrOutcome outcome;

    /** The tax return's due date read from the ATO, e.g. "02/11/2026"; null if not read. */
    private String dueDate;

    @When("the bot opens the ITR job and reads its client")
    public void bot_opens_the_itr_job_and_reads_its_client() throws InterruptedException {
        if (!JobType.ITR.isCurrent("ITR: open the job")) {
            return;
        }
        new FindJob();
        FindJob.openItrJob(jobCode());

        // The ITR process differs for an individual, a company, a trust and a group, and
        // only the individual one exists. Anything else stops here, but still gets its
        // row in ClientData so the sheet shows what was left and why.
        // "Individual" has its own process (this class). Every other Business Structure - Trust,
        // Group, Partnership, Spouse, Company - goes through the Trust scope, whose steps take over.
        // A structure that could not be read is not guessed at.
        if (JobType.isUnreadable(FindJob.businessStructure)) {
            System.out.println("!! " + jobCode() + ": the client's Business Structure could not be read from XPM"
                    + " — not collated rather than guessing Individual or not.");
            JobType.setCurrent(JobType.ITR_NOT_INDIVIDUAL);
            new ClientExcel().insertItrRow(jobCode(), MainClass.currentDate, FindJob.clientName,
                    FindJob.clientnamecode, FindJob.cientCode, FindJob.groupName, FindJob.internalteam,
                    FindJob.businessStructure, FindJob.preferredName, "", "",
                    FindJob.finalInvoiceNumbers, FindJob.finalInvoiceAmounts, false, "",
                    false, "", "", "ITR - Business Structure unknown, not collated");
        } else if (!JobType.isIndividual(FindJob.businessStructure)) {
            System.out.println(">>> " + jobCode() + " is an ITR for a '" + FindJob.businessStructure
                    + "' client — the Trust scope steps take over.");
            JobType.setCurrent(JobType.ITR_TRUST);
        }
    }

    @When("the bot finds the ITR document in the client folder")
    public void bot_finds_the_itr_document_in_the_client_folder() {
        if (!JobType.ITR.isCurrent("ITR: find the ITR document")) {
            return;
        }
        pack = collator.locate(jobCode(), clientName(), FindJob.cientCode, FindJob.groupName, FindJob.internalteam);
    }

    /**
     * Searches the client in the ATO and reads the tax return's due date from the
     * Summary's For action list. Every ITR job needs this, payable or not, so this is
     * where the ATO login happens for an ITR-only run.
     */
    @When("the bot reads the tax return due date from the ATO")
    public void bot_reads_the_tax_return_due_date_from_the_ato() throws InterruptedException {
        if (!JobType.ITR.isCurrent("ITR: read the due date from the ATO")) {
            return;
        }
        if (pack == null) {
            System.out.println("No ITR document was found — skipping the ATO due date.");
            return;
        }

        ATOLoginPage atoLoginPage = new ATOLoginPage();
        atoLoginPage.openAtoPortal();
        atoLoginPage.login(System.getProperty("ATO_Id"));
        if (!atoLoginPage.isLoggedIn()) {
            System.out.println("!! The ATO login was not completed — no due date for " + clientName()
                    + ". It is left blank on the cover letter.");
            return;
        }

        try {
            dueDate = atoLoginPage.readIncomeTaxDueDate(clientName(), ItrPaths.ITR_YEAR);
        } catch (RuntimeException e) {
            // The pack can still be built; the letter's Due Date is left blank and flagged.
            System.out.println("!! Could not read the due date from the ATO: " + e.getMessage());
        }
        if (dueDate == null) {
            System.out.println("!! No ATO due date for " + clientName() + " — the cover letter's Due Date stays blank.");
        }
    }

    @When("the bot downloads the ATO payment slip when the ITR position is payable")
    public void bot_downloads_the_ato_payment_slip_when_the_itr_position_is_payable() throws InterruptedException {
        if (!JobType.ITR.isCurrent("ITR: download the payment slip")) {
            return;
        }
        if (pack == null) {
            System.out.println("No ITR document was found — skipping the ATO payment slip.");
            return;
        }
        if (!pack.isPayable()) {
            System.out.println("Estimated position is not payable — skipping the ATO payment slip.");
            return;
        }

        System.out.println("Estimated position is payable — downloading the ATO payment slip for: " + clientName());

        ATOLoginPage atoLoginPage = new ATOLoginPage();
        atoLoginPage.openAtoPortal();
        atoLoginPage.login(System.getProperty("ATO_Id"));
        if (!atoLoginPage.isLoggedIn()) {
            System.out.println("!! The ATO login was not completed — no payment slip for " + clientName() + ".");
            return;
        }

        String downloaded = atoLoginPage.downloadIncomeTaxPaymentSlip(clientName());
        paymentSlip = downloaded == null ? null : Paths.get(DriverManager.getDownloadDirectory(), downloaded);
    }

    @Then("the bot collates the ITR pack")
    public void bot_collates_the_itr_pack() {
        if (!JobType.ITR.isCurrent("ITR: collate the pack")) {
            return;
        }
        if (pack == null) {
            System.out.println("!! Nothing collated for ITR job " + jobCode() + " — its ITR document was not found.");
        } else {
            // Engagement letters are off until the practice settles when one is due (scope
            // 3.5). When switched back on: no Ignition link means the letter goes in, and so
            // does a Description that could not be read — a spare is easier to spot than a gap.
            boolean attachEngagementLetter = ItrPaths.ATTACH_ENGAGEMENT_LETTER && FindJob.ignitionLink == null;
            if (attachEngagementLetter) {
                System.out.println(FindJob.descriptionRead
                        ? "No Ignition link in the job's Description — attaching the engagement letter."
                        : "!! Description not read — attaching the engagement letter to be safe. Check this job.");
            }
            // One name per person: a partnership's partners each get their own entity
            // line and signature column.
            List<String> engagementLetterNames = attachEngagementLetter
                    ? new EngagementLetterService().namesIn(clientName(), FindJob.businessStructure)
                    : null;
            outcome = collator.collate(pack, paymentSlip, dueDate,
                    FindJob.finalInvoiceNumbers, FindJob.finalInvoiceAmounts, engagementLetterNames);
            System.out.println("ITR collation for " + jobCode() + ": " + outcome);
        }
        writeClientDataRow();
    }

    /** One row in ClientData, marked ITR, written whether or not the ITR file was found. */
    private void writeClientDataRow() {
        ItrPositionReader.ItrPosition position = pack == null ? null : pack.position;

        new ClientExcel().insertItrRow(jobCode(), MainClass.currentDate, FindJob.clientName, FindJob.clientnamecode,
                FindJob.cientCode, FindJob.groupName, FindJob.internalteam, FindJob.businessStructure,
                FindJob.preferredName, pack == null ? "" : pack.recipientName, dueDate == null ? "" : dueDate,
                FindJob.finalInvoiceNumbers, FindJob.finalInvoiceAmounts,
                outcome != null && outcome.ffrWritten, outcome == null ? "" : outcome.expectedBalance,
                pack != null,
                position == null ? "" : position.isRefund() ? "Refund" : "Payable",
                position == null || position.getAmount() == null ? "" : position.getAmount());
    }

    /** Set by "Given the job code is" in {@link FindJobStepDefinition}. */
    private String jobCode() {
        return FindJobStepDefinition.jobCode;
    }

    /** The XPM client page name first, then the name on the job page. */
    private String clientName() {
        for (String candidate : new String[] {FindJob.clientnamecode, FindJob.clientName}) {
            if (candidate != null && !candidate.trim().isEmpty()
                    && !candidate.trim().toLowerCase().startsWith("no ")) {
                return candidate.trim();
            }
        }
        throw new IllegalStateException("No client name was read for ITR job " + jobCode());
    }
}
