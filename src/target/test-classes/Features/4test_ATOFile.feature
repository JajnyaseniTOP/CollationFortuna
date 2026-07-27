@order4
Feature: File Download Functionality

  Scenario: Download files from a list of links across multiple pages
    Given The user navigates through 100 pages
    When The user clicks on the download button and handles the download pop-up
    Then The user clicks on each link in the table to download the corresponding files