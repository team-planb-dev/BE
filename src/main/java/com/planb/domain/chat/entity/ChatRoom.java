package com.planb.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import com.planb.global.converter.BooleanToYNConverter;
import com.planb.global.jpa.BaseEntity;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class ChatRoom extends BaseEntity {

    @Id
    @Column(name = "chat_room_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String chatRoomName;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(nullable = false, length = 1)
    private boolean deleted;

    public void delete(){
        this.deleted = true;
        markDeleted();
    }

}
