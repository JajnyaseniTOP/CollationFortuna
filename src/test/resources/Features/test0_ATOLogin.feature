@order1
Feature: Prepare the client workbook

  The ATO leg no longer runs here. It needs the client name and the estimated tax
  position from the XPM job, so it runs last — see test5_ATOStatements.feature.

  Scenario: Create the workbook the collation run writes into
    Then the bot creates the client Excel sheet
