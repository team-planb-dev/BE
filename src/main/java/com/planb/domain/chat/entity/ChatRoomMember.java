package com.planb.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import com.planb.domain.user.entity.User;

@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_room_member",
                        columnNames = {"chat_room_id", "user_id"}
                )
        }
)
@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}
