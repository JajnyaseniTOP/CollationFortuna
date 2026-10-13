package StepDefinition;

import Driver_manager.DriverManager;
import Pages.ATOLoginPage;
import Pages.FindJob;
import com.jj.collation.ItrCollator;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The ITR (income tax return) run for one job.
 *
 * <p>Its own steps from start to finish, so the BAS steps in
 * {@link FindJobStepDefinition} and {@link ATOLoginStepDefinition} are not shared
 * with it and cannot be changed by it.
 *
 * <p>The order differs from BAS: the estimated position is read from the ITR document
 * in the client folder, so the folder search has to come before the ATO leg.
 */
public class ItrStepDefinition {

    private final ItrCollator collator = new ItrCollator();

    private String jobCode;
    private ItrCollator.ItrPack pack;
    private Path paymentSlip;

    @Given("the ITR job code is {string}")
    public void the_itr_job_code_is(String code) {
        jobCode = code;
    }

    @When("the bot opens the ITR job and reads its client")
    public void bot_opens_the_itr_job_and_reads_its_client() throws InterruptedException {
        new FindJob();
        FindJob.openItrJob(jobCode);
    }

    @When("the bot finds the ITR document in the client folder")
    public void bot_finds_the_itr_document_in_the_client_folder() {
        pack = collator.locate(jobCode, clientName(), FindJob.cientCode, FindJob.groupName, FindJob.internalteam);
    }

    @When("the bot downloads the ATO payment slip when the ITR position is payable")
    public void bot_downloads_the_ato_payment_slip_when_the_itr_position_is_payable() throws InterruptedException {
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
        if (pack == null) {
            System.out.println("!! Nothing collated for ITR job " + jobCode + " — its ITR document was not found.");
            return;
        }
        System.out.println("ITR collation for " + jobCode + ": " + collator.collate(pack, paymentSlip));
    }

    /** The XPM client page name first, then the name on the job page. */
    private String clientName() {
        for (String candidate : new String[] {FindJob.clientnamecode, FindJob.clientName}) {
            if (candidate != null && !candidate.trim().isEmpty()
                    && !candidate.trim().toLowerCase().startsWith("no ")) {
                return candidate.trim();
            }
        }
        throw new IllegalStateException("No client name was read for ITR job " + jobCode);
    }
}
