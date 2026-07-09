package com.wellconverge.membership.application.bdd;

import com.wellconverge.membership.application.EmailAlreadyRegisteredException;
import com.wellconverge.membership.application.port.in.CompleteOnboardingCommand;
import com.wellconverge.membership.application.port.in.MemberView;
import com.wellconverge.membership.application.port.in.RegisterMemberCommand;
import com.wellconverge.membership.application.service.CompleteOnboardingService;
import com.wellconverge.membership.application.service.GetMemberService;
import com.wellconverge.membership.application.service.RegisterMemberService;
import com.wellconverge.membership.domain.AlreadyOnboardedException;
import com.wellconverge.membership.domain.DomainException;
import com.wellconverge.membership.domain.EmailAddress;
import com.wellconverge.membership.domain.MemberId;
import com.wellconverge.membership.domain.WellnessGoal;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Glue between the Gherkin specs and the Membership use cases (driven through the ports). */
public class MembershipStepDefinitions {

    private InMemoryMemberRepository repository;
    private RecordingEventPublisher publisher;
    private RegisterMemberService registerMember;
    private CompleteOnboardingService completeOnboarding;
    private GetMemberService getMember;

    private MemberId currentMemberId; // "that member" across steps
    private RuntimeException caughtError;

    @Given("the membership platform is running")
    public void the_membership_platform_is_running() {
        repository = new InMemoryMemberRepository();
        publisher = new RecordingEventPublisher();
        Clock clock = Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneOffset.UTC);
        registerMember = new RegisterMemberService(repository, publisher, clock);
        completeOnboarding = new CompleteOnboardingService(repository, publisher, clock);
        getMember = new GetMemberService(repository);
        currentMemberId = null;
        caughtError = null;
    }

    // --- Registration ---

    @When("I register with email {string} and name {string}")
    public void i_register_with_email_and_name(String email, String name) {
        try {
            currentMemberId = registerMember.register(new RegisterMemberCommand(email, name));
        } catch (RuntimeException e) {
            caughtError = e;
        }
    }

    @Given("a member is already registered with email {string}")
    public void a_member_is_already_registered_with_email(String email) {
        currentMemberId = registerMember.register(
                new RegisterMemberCommand(email, "Existing Member"));
    }

    @Then("the registration succeeds")
    public void the_registration_succeeds() {
        assertThat(caughtError).isNull();
        assertThat(currentMemberId).isNotNull();
    }

    @Then("a member exists with email {string} and status {string}")
    public void a_member_exists_with_email_and_status(String email, String status) {
        var member = repository.findByEmail(EmailAddress.of(email));
        assertThat(member).isPresent();
        assertThat(member.get().status().name()).isEqualTo(status);
    }

    @Then("the registration is rejected because the email is already registered")
    public void the_registration_is_rejected_email_taken() {
        assertThat(caughtError).isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Then("the registration is rejected as invalid")
    public void the_registration_is_rejected_as_invalid() {
        assertThat(caughtError)
                .isInstanceOf(DomainException.class)
                .isNotInstanceOf(EmailAlreadyRegisteredException.class);
    }

    // --- Onboarding ---

    @When("that member completes onboarding with goals {string}")
    public void that_member_completes_onboarding_with_goals(String goals) {
        try {
            MemberView view = completeOnboarding.completeOnboarding(
                    new CompleteOnboardingCommand(currentMemberId.toString(), parseGoals(goals), null));
            assertThat(view).isNotNull();
        } catch (RuntimeException e) {
            caughtError = e;
        }
    }

    @Given("that member has already completed onboarding with goals {string}")
    public void that_member_has_already_completed_onboarding_with_goals(String goals) {
        completeOnboarding.completeOnboarding(
                new CompleteOnboardingCommand(currentMemberId.toString(), parseGoals(goals), null));
    }

    @Then("onboarding succeeds")
    public void onboarding_succeeds() {
        assertThat(caughtError).isNull();
    }

    @Then("that member has status {string}")
    public void that_member_has_status(String status) {
        assertThat(getMember.getById(currentMemberId).status()).isEqualTo(status);
    }

    @Then("that member has goals {string}")
    public void that_member_has_goals(String goals) {
        assertThat(getMember.getById(currentMemberId).goals()).isEqualTo(parseGoals(goals));
    }

    @Then("onboarding is rejected as invalid")
    public void onboarding_is_rejected_as_invalid() {
        assertThat(caughtError)
                .isInstanceOf(DomainException.class)
                .isNotInstanceOf(AlreadyOnboardedException.class);
    }

    @Then("onboarding is rejected because the member is already onboarded")
    public void onboarding_is_rejected_already_onboarded() {
        assertThat(caughtError).isInstanceOf(AlreadyOnboardedException.class);
    }

    // --- Events ---

    @Then("a {string} event was published")
    public void an_event_was_published(String simpleClassName) {
        assertThat(publisher.published())
                .anyMatch(e -> e.getClass().getSimpleName().equals(simpleClassName));
    }

    // --- Helpers ---

    private static Set<WellnessGoal> parseGoals(String csv) {
        Set<WellnessGoal> goals = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(WellnessGoal::valueOf)
                .collect(Collectors.toSet());
        return goals.isEmpty() ? Set.of() : EnumSet.copyOf(goals);
    }
}
