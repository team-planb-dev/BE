package com.planb.domain.chat.dto.response;

import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;

public record AiReplyContent(String message,
                             EditPlanPreviewResponse editPreview) {
}
