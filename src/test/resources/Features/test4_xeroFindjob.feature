@order5
Feature: Collate each job

  Every job — BAS or ITR — is listed below. The bot reads the job's Name in the XPM job
  manager first: "Income Tax Return" (or ITR) makes it an ITR job, "BAS", "IAS" or
  "Activity statement" a BAS job. The steps for the other kind then skip themselves,
  and a job whose name says neither is left alone.

  BAS: find it in Xero, read the client and its tax returns, look the client up in the
  ATO, then write its rows and collate each period.

  ITR: find 2026_ITR_ClientName in the client folder and read the estimated position
  off it, fetch the ATO payment slip only when payable, then build the Collation
  Testing pack and the final PDF.

  Scenario Outline: Collate one job
    Given the job code is "<jobCode>"
    When the bot decides BAS or ITR from the job name

    # BAS
    When I switch the portal based on the job code
    And the bot opens the ATO portal for the job's client
    And the bot reads the BAS lodgement cycle
    And the bot downloads the ATO statements when the estimated position is payable
    Then the bot writes the ClientData rows for the BAS period

    # ITR
    When the bot opens the ITR job and reads its client
    And the bot finds the ITR document in the client folder
    And the bot downloads the ATO payment slip when the ITR position is payable
    Then the bot collates the ITR pack

    Examples:
      | jobCode   |
      | KPF019886 |
      | KPF018802 |
      | KPF019526 |
