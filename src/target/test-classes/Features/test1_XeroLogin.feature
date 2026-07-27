@order1

Feature: Verify User is able to Login xero

  Scenario: user is on xero login Page
    Given User enter email and password
    When User click on Enter button
    Then User pass security question