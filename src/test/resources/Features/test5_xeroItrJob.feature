@order6 @itr
Feature: Collate each ITR job

  An income tax return job runs its own sequence: find it in Xero, read its client,
  find 2026_ITR_ClientName in the client folder and read the estimated position off
  it, fetch the ATO payment slip only when that position is payable, then build the
  Collation Testing pack.

  Separate from the BAS feature so the two never share a step. Add one row per ITR
  job below; with no rows this feature runs nothing.

  Scenario Outline: Collate one ITR job
    Given the ITR job code is "<jobCode>"
    When the bot opens the ITR job and reads its client
    And the bot finds the ITR document in the client folder
    And the bot downloads the ATO payment slip when the ITR position is payable
    Then the bot collates the ITR pack

    Examples:
      | jobCode |
      | KPF019526 |
      | KPF019526 | 
