package com.wellconverge.membership.adapters.rest;

import com.wellconverge.membership.application.MemberNotFoundException;
import com.wellconverge.membership.application.port.in.CompleteOnboardingUseCase;
import com.wellconverge.membership.application.port.in.GetMemberUseCase;
import com.wellconverge.membership.application.port.in.MemberView;
import com.wellconverge.membership.application.port.in.RegisterMemberUseCase;
import com.wellconverge.membership.domain.MemberId;
import com.wellconverge.membership.domain.WellnessGoal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A prior version of this controller declared {@code @PathVariable String id} without an
 * explicit name, which threw at request time whenever javac wasn't compiled with
 * {@code -parameters} (500, never caught by the module's HTTP-free BDD suite). These tests
 * exercise the controller through real Spring MVC request handling to guard against that class
 * of regression.
 */
@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegisterMemberUseCase registerMember;

    @MockBean
    private CompleteOnboardingUseCase completeOnboarding;

    @MockBean
    private GetMemberUseCase getMember;

    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Test
    void getById_resolvesThePathVariable() throws Exception {
        MemberView view = new MemberView(
                MEMBER_ID.toString(), "ada@example.com", "Ada Lovelace", "REGISTERED",
                Set.of(), null, Instant.now(), null);
        when(getMember.getById(MemberId.of(MEMBER_ID))).thenReturn(view);

        mockMvc.perform(get("/api/members/{id}", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void getById_returns404WhenMemberNotFound() throws Exception {
        when(getMember.getById(MemberId.of(MEMBER_ID))).thenThrow(new MemberNotFoundException(MEMBER_ID.toString()));

        mockMvc.perform(get("/api/members/{id}", MEMBER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void onboard_resolvesThePathVariable() throws Exception {
        MemberView view = new MemberView(
                MEMBER_ID.toString(), "ada@example.com", "Ada Lovelace", "ONBOARDED",
                Set.of(WellnessGoal.SLEEP_BETTER), LocalDate.of(1990, 1, 1), Instant.now(), Instant.now());
        when(completeOnboarding.completeOnboarding(any())).thenReturn(view);

        mockMvc.perform(post("/api/members/{id}/onboarding", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goals":["SLEEP_BETTER"],"dateOfBirth":"1990-01-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONBOARDED"));

        verify(completeOnboarding).completeOnboarding(
                argThat(cmd -> cmd.memberId().equals(MEMBER_ID.toString())));
    }
}
