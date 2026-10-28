package StepDefinition;

import Pages.ATOLoginPage;
import Pages.FindJob;
import com.jj.collation.JobType;
import com.jj.collation.TrustCollator;
import com.jj.util.ClientExcel;
import com.jj.util.MainClass;
import FileExplorerCollation.filesearch.config.ItrPaths;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Trust ITR run for one job (scope 4). It follows the shared "opens the ITR job"
 * step, which reads the job and its client and hands over here when the client's
 * Business Structure is a trust. Every step does nothing for any other job.
 */
public class TrustStepDefinition {

    private final TrustCollator collator = new TrustCollator();
    private TrustCollator.TrustPack pack;
    private TrustCollator.TrustOutcome outcome;

    @When("the bot finds the trust entities' documents in the client folders")
    public void bot_finds_the_trust_entities_documents() {
        if (!JobType.ITR_TRUST.isCurrent("Trust: find the documents")) {
            return;
        }
        pack = collator.locate(FindJobStepDefinition.jobCode, clientName(), FindJob.cientCode, FindJob.groupName,
                FindJob.internalteam, FindJob.entitiesIncluded);
    }

    /** The trust's own position and due date are NIL; every other entity's due date comes from the ATO. */
    @When("the bot reads the trust entities' due dates from the ATO")
    public void bot_reads_the_trust_entities_due_dates() throws InterruptedException {
        if (!JobType.ITR_TRUST.isCurrent("Trust: read the due dates from the ATO")) {
            return;
        }
        if (pack == null) {
            System.out.println("No Trust pack was found — skipping the ATO due dates.");
            return;
        }

        boolean loggedIn = false;
        ATOLoginPage atoLoginPage = new ATOLoginPage();

        for (TrustCollator.TrustEntity entity : pack.entities) {
            if (entity.kind == TrustCollator.Kind.TRUST) {
                entity.dueDate = "NIL";
                continue;
            }
            if (entity.returnFile == null) {
                continue;
            }
            if (!loggedIn) {
                atoLoginPage.openAtoPortal();
                atoLoginPage.login(System.getProperty("ATO_Id"));
                loggedIn = true;
                // Without a completed myID login every search would only fail, slowly
                if (!atoLoginPage.isLoggedIn()) {
                    System.out.println("!! The ATO login was not completed — no due dates for this job. "
                            + "They are left blank on the cover letter.");
                    return;
                }
            }
            try {
                String due = atoLoginPage.readIncomeTaxDueDate(entity.name, ItrPaths.ITR_YEAR);
                entity.dueDate = due == null ? "" : due;
            } catch (RuntimeException e) {
                System.out.println("!! Could not read the ATO due date for " + entity.name + ": " + e.getMessage());
            }
            if (entity.dueDate.isEmpty()) {
                System.out.println("!! No ATO due date for " + entity.name + " — left blank on the cover letter.");
            }
        }
    }

    @Then("the bot collates the trust pack")
    public void bot_collates_the_trust_pack() {
        if (!JobType.ITR_TRUST.isCurrent("Trust: collate the pack")) {
            return;
        }
        if (pack == null) {
            System.out.println("!! Nothing collated for Trust job " + FindJobStepDefinition.jobCode
                    + " — the trust's folder was not found.");
            writeRow(null);
            return;
        }

        outcome = collator.collate(pack, FindJob.coverLetterNotes, FindJob.finalInvoiceNumbers,
                FindJob.finalInvoiceAmounts, FindJob.isIgnitionJob());
        System.out.println("Trust collation for " + FindJobStepDefinition.jobCode + ": " + outcome);

        for (TrustCollator.TrustEntity entity : pack.entities) {
            writeRow(entity);
        }
    }

    /** One ClientData row per entity, so each entity's position and due date has its own cells. */
    private void writeRow(TrustCollator.TrustEntity entity) {
        Map<Integer, String> cells = new LinkedHashMap<>();
        cells.put(ClientExcel.JOB_NO_COLUMN, FindJobStepDefinition.jobCode);
        cells.put(ClientExcel.COVER_LETTER_NOTES_COLUMN, FindJob.coverLetterNotes);
        cells.put(ClientExcel.DATE_COLUMN, MainClass.currentDate);
        cells.put(ClientExcel.CLIENT_NAME_COLUMN, FindJob.clientName);
        cells.put(ClientExcel.CLIENT_NAME_XPM_COLUMN, FindJob.clientnamecode);
        cells.put(ClientExcel.CLIENT_CODE_COLUMN, FindJob.cientCode);
        cells.put(ClientExcel.GROUP_COLUMN, FindJob.groupName);
        cells.put(ClientExcel.TEAM_COLUMN, FindJob.internalteam);
        cells.put(ClientExcel.BUSINESS_STRUCTURE, FindJob.businessStructure);
        cells.put(ClientExcel.COLLATION_TYPE, "ITR - Trust");
        cells.put(ClientExcel.PREFERRED_NAME, FindJob.preferredName);
        cells.put(ClientExcel.FINAL_INVOICE_NO, FindJob.finalInvoiceNumbers);
        cells.put(ClientExcel.FINAL_INVOICE_AMOUNT, FindJob.finalInvoiceAmounts);
        cells.put(ClientExcel.ENTITIES_INCLUDED_COLUMN, FindJob.entitiesIncluded.replace("\n", "; "));
        cells.put(ClientExcel.IGNITION_COLUMN, FindJob.isIgnitionJob() ? "Yes (" + FindJob.clientOrderNo + ")" : "No");

        if (pack != null) {
            cells.put(ClientExcel.RECIPIENT, String.join(", ", pack.recipients));
            cells.put(ClientExcel.FINANCIAL_STATEMENT_COLUMN, pack.financialStatement == null ? "Not Found"
                    : pack.financialStatement.getFileName().toString());
            cells.put(ClientExcel.DIRECTORS_COLUMN, String.join(", ", pack.directors));
        }
        if (outcome != null) {
            cells.put(ClientExcel.ENGAGEMENT_LETTER_COLUMN, outcome.engagementLetterWritten ? "Yes" : "No");
        }

        if (entity != null) {
            cells.put(ClientExcel.ENTITY_COLUMN, entity.name + " (" + entity.kind.name().toLowerCase() + ")");
            cells.put(ClientExcel.STATUS_COLUMN, entity.returnFile == null ? "Not Found" : "Found");
            if (entity.kind == TrustCollator.Kind.TRUST) {
                cells.put(ClientExcel.PAYABLE_REFUND_COLUMN, "NIL");
                cells.put(ClientExcel.DUE_DATE, "NIL");
            } else {
                cells.put(ClientExcel.PAYABLE_REFUND_COLUMN, entity.position == null ? ""
                        : entity.position.isRefund() ? "Refund" : "Payable");
                cells.put(ClientExcel.AMOUNT_COLUMN, entity.position == null || entity.position.getAmount() == null
                        ? "" : entity.position.getAmount());
                cells.put(ClientExcel.DUE_DATE, entity.dueDate);
            }
            cells.put(ClientExcel.FFR_PREPARED, entity.ffrWritten ? "Yes" : "No");
            cells.put(ClientExcel.FFR_EXPECTED_BALANCE, entity.expectedBalance);
        }

        new ClientExcel().insertCells(cells);
    }

    private String clientName() {
        List<String> candidates = new ArrayList<>(List.of(
                FindJob.clientnamecode == null ? "" : FindJob.clientnamecode,
                FindJob.clientName == null ? "" : FindJob.clientName));
        for (String candidate : candidates) {
            if (!candidate.trim().isEmpty() && !candidate.trim().toLowerCase().startsWith("no ")) {
                return candidate.trim();
            }
        }
        throw new IllegalStateException("No client name was read for Trust job " + FindJobStepDefinition.jobCode);
    }
}
