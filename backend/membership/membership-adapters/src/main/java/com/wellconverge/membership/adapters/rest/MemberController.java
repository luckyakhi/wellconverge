package com.wellconverge.membership.adapters.rest;

import com.wellconverge.membership.application.port.in.CompleteOnboardingCommand;
import com.wellconverge.membership.application.port.in.CompleteOnboardingUseCase;
import com.wellconverge.membership.application.port.in.GetMemberUseCase;
import com.wellconverge.membership.application.port.in.MemberView;
import com.wellconverge.membership.application.port.in.RegisterMemberCommand;
import com.wellconverge.membership.application.port.in.RegisterMemberUseCase;
import com.wellconverge.membership.domain.MemberId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/** Inbound REST adapter for the Membership context. Depends only on the inbound ports. */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final RegisterMemberUseCase registerMember;
    private final CompleteOnboardingUseCase completeOnboarding;
    private final GetMemberUseCase getMember;

    public MemberController(RegisterMemberUseCase registerMember,
                            CompleteOnboardingUseCase completeOnboarding,
                            GetMemberUseCase getMember) {
        this.registerMember = registerMember;
        this.completeOnboarding = completeOnboarding;
        this.getMember = getMember;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterMemberRequest request,
                                                        UriComponentsBuilder uriBuilder) {
        MemberId id = registerMember.register(new RegisterMemberCommand(request.email(), request.fullName()));
        URI location = uriBuilder.path("/api/members/{id}").build(id.toString());
        return ResponseEntity.created(location).body(Map.of("id", id.toString()));
    }

    @PostMapping("/{id}/onboarding")
    public MemberResponse onboard(@PathVariable String id,
                                  @Valid @RequestBody CompleteOnboardingRequest request) {
        MemberView view = completeOnboarding.completeOnboarding(
                new CompleteOnboardingCommand(id, request.goals(), request.dateOfBirth()));
        return MemberResponse.from(view);
    }

    @GetMapping("/{id}")
    public MemberResponse getById(@PathVariable String id) {
        return MemberResponse.from(getMember.getById(MemberId.of(id)));
    }
}
