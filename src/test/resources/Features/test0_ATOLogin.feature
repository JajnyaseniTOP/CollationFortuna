@order1
Feature: Start the workflow from the ATO portal

  Scenario: Launch ATO and prepare the client workbook
    Given the user launches the ATO website
    Then the bot creates the client Excel sheet
    And the extracted ATO value is added to the second column