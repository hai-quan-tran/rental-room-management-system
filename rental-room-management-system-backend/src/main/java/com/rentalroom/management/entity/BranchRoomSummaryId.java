package com.rentalroom.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BranchRoomSummaryId implements Serializable {

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "room_type_id")
    private Long roomTypeId;
}
