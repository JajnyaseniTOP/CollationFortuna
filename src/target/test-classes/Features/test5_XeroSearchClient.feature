Feature: Xero Client Search

  Scenario: Search for a client and retrieve client code and email
    Given I am on the Xero search client page
    When I input the client name
    And I click on the search button
    Then I should see the client code