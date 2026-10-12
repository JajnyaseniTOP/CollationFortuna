package StepDefinition;

import Driver_manager.DriverManager;
import Pages.ATOLoginPage;
import Pages.FindJob;
import com.jj.util.ClientExcel;
import com.jj.util.MainClass;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

/**
 * The ATO leg of the run.
 *
 * <p>It deliberately runs after the job has been read out of XPM: the client name
 * has to come from the job rather than a hard-coded default, and the lodgement
 * cycle has to be known before the BAS period can be written to ClientData.xls.
 */
public class ATOLoginStepDefinition {

    private final ATOLoginPage atoLoginPage = new ATOLoginPage();
    private final ClientExcel clientExcel = new ClientExcel();

    @Given("the user launches the ATO website")
    public void user_launch_website() throws InterruptedException {
        atoLoginPage.user_launch_website();
    }

    @Then("the bot creates the client Excel sheet")
    public void bot_creates_client_excel_sheet() {
        clientExcel.createEmptyExcelSheet();
    }

    @Given("the bot opens the ATO portal for the job's client")
    public void bot_opens_the_ato_portal_for_the_jobs_client() throws InterruptedException {
        atoLoginPage.openAtoPortal();
        atoLoginPage.login(System.getProperty("ATO_Id"));
    }

    @When("the bot reads the BAS lodgement cycle")
    public void bot_reads_the_bas_lodgement_cycle() throws InterruptedException {
        String clientName = requireClientName();

        FindJob.atoQuarter = atoLoginPage.findWithholdingRegistration(clientName);
        if (FindJob.atoQuarter == null || FindJob.atoQuarter.trim().isEmpty()) {
            FindJob.atoQuarter = "NA";
        }
        System.out.println("BAS lodgement cycle for " + clientName + ": " + FindJob.atoQuarter);
    }

    @When("the bot downloads the ATO statements when the estimated position is payable")
    public void bot_downloads_ato_statements_when_estimated_position_is_payable() throws InterruptedException {
        if (!FindJob.estimatedPositionPayable) {
            System.out.println("Estimated tax position is not payable — skipping the ATO statement download.");
            return;
        }

        String clientName = requireClientName();
        System.out.println("Estimated position is payable — downloading ATO statements for: " + clientName);

        List<String> downloaded = atoLoginPage.downloadAccountStatements(clientName);
        System.out.println("ATO statements downloaded: " + downloaded);

        // Remembered so the collation can copy it into the client's period folders.
        FindJob.lastAtoStatement = downloaded.isEmpty() ? null
                : java.nio.file.Paths.get(DriverManager.getDownloadDirectory(), downloaded.get(0));
    }

    @Then("the bot writes the ClientData rows for the BAS period")
    public void bot_writes_the_clientdata_rows_for_the_bas_period() {
        FindJob.writeClientDataRows();
    }

    /** The XPM client page name first, then the job page, then the configured default. */
    private String requireClientName() {
        for (String candidate : new String[] {FindJob.clientnamecode, FindJob.clientName, MainClass.clientName}) {
            if (candidate != null && !candidate.trim().isEmpty()
                    && !candidate.trim().toLowerCase().startsWith("no ")) {
                return candidate.trim();
            }
        }
        throw new IllegalStateException("No client name available for the ATO search");
    }
}
