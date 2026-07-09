Feature: Complete onboarding
  As a registered member
  I want to declare my wellness goals
  So that WellConverge can tailor my journey

  Background:
    Given the membership platform is running
    And a member is already registered with email "ada@example.com"

  Scenario: A registered member completes onboarding with goals
    When that member completes onboarding with goals "SLEEP_BETTER, MOVE_MORE"
    Then onboarding succeeds
    And that member has status "ONBOARDED"
    And that member has goals "SLEEP_BETTER, MOVE_MORE"
    And a "MemberOnboarded" event was published

  Scenario: Onboarding without any goal is rejected
    When that member completes onboarding with goals ""
    Then onboarding is rejected as invalid

  Scenario: Completing onboarding a second time is rejected
    Given that member has already completed onboarding with goals "EAT_WELL"
    When that member completes onboarding with goals "STRESS_LESS"
    Then onboarding is rejected because the member is already onboarded
