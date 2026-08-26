package StepDefinition;

import Pages.ATOLoginPage;
import Pages.FindJob;
import com.jj.util.ClientExcel;
import com.jj.util.MainClass;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

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

    @Then("the extracted ATO value is added to the second column")
    public void extracted_ato_value_is_added_to_second_column() throws InterruptedException {
        atoLoginPage.login(System.getProperty("ATO_Id"));
        String clientName = MainClass.clientName;
        if (clientName == null || clientName.trim().isEmpty()) {
            throw new IllegalStateException("MainClass.clientName is empty");
        }
        FindJob.atoQuarter = atoLoginPage.findWithholdingRegistration(clientName);
        clientExcel.addAtoValue(FindJob.atoQuarter);
    }
}
