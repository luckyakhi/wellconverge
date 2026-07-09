Feature: Register a member
  As a prospective member
  I want to create a WellConverge account with my email and name
  So that I can start my wellness journey

  Background:
    Given the membership platform is running

  Scenario: A new member registers with valid details
    When I register with email "ada@example.com" and name "Ada Lovelace"
    Then the registration succeeds
    And a member exists with email "ada@example.com" and status "REGISTERED"
    And a "MemberRegistered" event was published

  Scenario: Registering with an email already in use is rejected
    Given a member is already registered with email "grace@example.com"
    When I register with email "grace@example.com" and name "Grace Hopper"
    Then the registration is rejected because the email is already registered

  Scenario: Registering with a malformed email is rejected
    When I register with email "not-an-email" and name "Alan Turing"
    Then the registration is rejected as invalid
