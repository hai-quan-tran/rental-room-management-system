package com.rentalroom.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Read-only mapping of the {@code v_branch_room_summary} view (room counts derived, never stored). */
@Entity
@Immutable
@Table(name = "v_branch_room_summary")
@Getter
@NoArgsConstructor
public class BranchRoomSummary {

    @EmbeddedId
    private BranchRoomSummaryId id;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "room_type_name")
    private String roomTypeName;

    /** {@code COUNT(...)} — MySQL returns BIGINT, matches Long directly. */
    @Column(name = "room_count")
    private Long roomCount;

    /** {@code SUM(CASE WHEN ... THEN 1 ELSE 0 END)} over integer literals — MySQL returns DECIMAL. */
    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "empty_room_count")
    private Long emptyRoomCount;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "occupied_room_count")
    private Long occupiedRoomCount;
}
