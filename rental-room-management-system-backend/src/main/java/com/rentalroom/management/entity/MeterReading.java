package com.rentalroom.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Old/new meter reading for 1 room + 1 metered {@link ExtraFeeCategory} + 1 billing month.
 * Scoped to {@code room_id} (not contract) — the meter is physical room infrastructure, same as
 * {@code room.wifiFee}/{@code room.parkingFee}, independent of whichever contract is active.
 */
@Entity
@Table(name = "meter_reading",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "extra_fee_category_id", "bill_year", "bill_month"}))
@Getter
@Setter
@NoArgsConstructor
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extra_fee_category_id", nullable = false)
    private ExtraFeeCategory extraFeeCategory;

    /** DB column is TINYINT UNSIGNED — override the JDBC type so schema validation matches. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "bill_month", nullable = false)
    private Integer billMonth;

    /** DB column is SMALLINT UNSIGNED — override the JDBC type so schema validation matches. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "bill_year", nullable = false)
    private Integer billYear;

    @Column(name = "old_reading", precision = 10, scale = 2, nullable = false)
    private BigDecimal oldReading;

    @Column(name = "new_reading", precision = 10, scale = 2, nullable = false)
    private BigDecimal newReading;

    /** Generated column ({@code new_reading - old_reading}) — read-only, DB computes it. */
    @Column(name = "consumption", precision = 10, scale = 2, insertable = false, updatable = false)
    private BigDecimal consumption;

    /** Audit metadata only — the acting account id, not a managed association. */
    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "note", length = 300)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MeterReading other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
