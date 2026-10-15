package StepDefinition;

import Pages.FindJob;
import Pages.ATOLoginPage;
import com.jj.collation.JobType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

public class FindJobStepDefinition {

    /** Shared with the ITR steps, which run in the same scenario but a different class. */
    static String jobCode;

    FindJob job = new FindJob();
    ATOLoginPage ato=new ATOLoginPage();

    @Given("the job code is {string}")
    public void the_job_code_is(String code) {
        jobCode = code;
        JobType.setCurrent(JobType.UNKNOWN);
    }

    /**
     * Reads the job's Name in the job manager and decides BAS or ITR from it. The BAS
     * and ITR steps that follow each skip a job that is not theirs.
     */
    @When("the bot decides BAS or ITR from the job name")
    public void the_bot_decides_bas_or_itr_from_the_job_name() throws InterruptedException {
        String name = FindJob.readJobNameFromJobList(jobCode);
        JobType type = JobType.fromJobName(name);
        JobType.setCurrent(type);

        if (type == JobType.UNKNOWN) {
            System.out.println("!! " + jobCode + " is not collated: its name '" + name
                    + "' says neither BAS nor Income Tax Return (or says both).");
        } else {
            System.out.println(">>> " + jobCode + " will be collated as " + type);
        }
    }

    @When("I switch the portal based on the job code")
    public void i_switch_the_portal_based_on_the_job_code() throws InterruptedException {
        if (!JobType.BAS.isCurrent("BAS: open the job")) {
            return;
        }
    	job.switchPortalByPrefix(jobCode);
    }

    @Then("the correct portal should be selected")
    public void the_correct_portal_should_be_selected() {

    }
}
