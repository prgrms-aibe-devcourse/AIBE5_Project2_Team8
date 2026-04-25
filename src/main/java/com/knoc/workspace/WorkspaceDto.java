package com.knoc.workspace;

import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.github.dto.GithubPrMetadata;
import lombok.Builder;

import java.util.List;

@Builder
public record WorkspaceDto(
        Long orderId,
        String orderStatus,
        Long chatRoomId,
        boolean chatRoomActive,
        String currentNickname,
        String opponentNickname,
        String opponentAvatarUrl,
        List<ChatMessageResponse> initialMessages,
        String githubPrUrl,
        String projectContext,
        String concernPoint,
        GithubPrMetadata prMetadata,
        String reportIndustryPerspective,
        String reportEdgeCases,
        String reportAlternatives,
        String reportAiSummary,
        boolean hasReport,
        boolean hasReview,
        boolean isSenior
) {}