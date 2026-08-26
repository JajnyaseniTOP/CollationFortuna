@order5
Feature: Switch Portal based on Job Code

  As a user
  I want the system to switch to the correct portal
  based on the prefix of the job code

  Scenario Outline: Switch portal depending on job code prefix
    Given the job code is "<jobCode>"
    When I switch the portal based on the job code
    Then the correct portal should be selected

    Examples:
      | jobCode     |    
      | J010726     |
     

     