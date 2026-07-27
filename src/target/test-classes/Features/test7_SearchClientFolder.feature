Feature: Search Client Folder and Copy Files

  Scenario: Verify client folders and file copying process
    Given I have the client data
    When I process the client files
    Then the client folder should be created
    And the ATO Correspondence folder should be created
    And the file should be copied successfully