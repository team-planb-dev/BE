package com.planb.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import com.planb.domain.user.entity.User;
import com.planb.global.converter.BooleanToYNConverter;
import com.planb.global.jpa.BaseEntity;

import java.time.Instant;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id",nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id",nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column
    private Instant sendAt;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(nullable = false, length = 1)
    private boolean deleted;

    public void deleteOneMessage(){
        this.deleted = true;
        markDeleted();
    }

}
