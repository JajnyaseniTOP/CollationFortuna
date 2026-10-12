@order5
Feature: Collate each BAS job

  Every job runs the whole sequence for itself: find it in Xero, read the client and
  its tax returns, look that client up in the ATO, then write its rows.

  The ATO leg used to run once after all the jobs, which meant only the last job was
  ever looked up in the ATO and only its rows reached the sheet.

  Scenario Outline: Collate one job
    Given the job code is "<jobCode>"
    When I switch the portal based on the job code
    And the bot opens the ATO portal for the job's client
    And the bot reads the BAS lodgement cycle
    And the bot downloads the ATO statements when the estimated position is payable
    Then the bot writes the ClientData rows for the BAS period

    Examples:
      | jobCode     |
      | KPF019886   |
      | KPF018802   |
