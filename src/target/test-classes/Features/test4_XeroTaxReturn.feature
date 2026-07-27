Feature: Process Notice of Assessment and fill in client details

  Scenario: Process all clients with Notice of Assessment
    Given  the system processes each client with Notice of Assessment
    Then it should extract the Date of Issue, ATO Reference, Taxable Income, and Result Amount from the PDF
    And it should fill the Date of Issue, ATO Reference, Taxable Income, and Result Amount in the web form for each client 