package StepDefinition;

import Driver_manager.DriverManager;
import Pages.ATOLoginPage;
import Pages.FindJob;
import com.jj.collation.ItrCollator;
import com.jj.collation.JobType;
import com.jj.util.ClientExcel;
import com.jj.util.MainClass;
import FileExplorerCollation.filesearch.document.ItrPositionReader;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Path;
import java.nio.file.Paths;

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

    @When("the bot opens the ITR job and reads its client")
    public void bot_opens_the_itr_job_and_reads_its_client() throws InterruptedException {
        if (!JobType.ITR.isCurrent("ITR: open the job")) {
            return;
        }
        new FindJob();
        FindJob.openItrJob(jobCode());
    }

    @When("the bot finds the ITR document in the client folder")
    public void bot_finds_the_itr_document_in_the_client_folder() {
        if (!JobType.ITR.isCurrent("ITR: find the ITR document")) {
            return;
        }
        pack = collator.locate(jobCode(), clientName(), FindJob.cientCode, FindJob.groupName, FindJob.internalteam);
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
            // No Ignition link means no signed engagement through Ignition, so the letter
            // goes in. If the Description could not be read at all, it goes in too: a
            // spare letter is easier to spot and remove than a missing one.
            boolean attachEngagementLetter = FindJob.ignitionLink == null;
            if (attachEngagementLetter) {
                System.out.println(FindJob.descriptionRead
                        ? "No Ignition link in the job's Description — attaching the engagement letter."
                        : "!! Description not read — attaching the engagement letter to be safe. Check this job.");
            }
            System.out.println("ITR collation for " + jobCode() + ": "
                    + collator.collate(pack, paymentSlip, attachEngagementLetter));
        }
        writeClientDataRow();
    }

    /** One row in ClientData, marked ITR, written whether or not the ITR file was found. */
    private void writeClientDataRow() {
        ItrPositionReader.ItrPosition position = pack == null ? null : pack.position;

        new ClientExcel().insertItrRow(jobCode(), MainClass.currentDate, FindJob.clientName, FindJob.clientnamecode,
                FindJob.cientCode, FindJob.groupName, FindJob.internalteam, FindJob.businessStructure,
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
