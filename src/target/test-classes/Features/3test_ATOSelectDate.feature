@order3

Feature: Verify User is able to search for particular date

  Scenario: user search for a particular date
    Given User click on search option
    When User click on last 24 hours
    And User unselect SMS option
    Then User click on search button    