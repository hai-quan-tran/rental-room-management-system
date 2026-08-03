package com.rentalroom.management.entity;

import com.rentalroom.management.enums.PaymentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "monthly_bill",
        uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "bill_year", "bill_month"}))
@Getter
@Setter
@NoArgsConstructor
public class MonthlyBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    /** DB column is TINYINT UNSIGNED — override the JDBC type so schema validation matches. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "bill_month", nullable = false)
    private Integer billMonth;

    /** DB column is SMALLINT UNSIGNED — override the JDBC type so schema validation matches. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "bill_year", nullable = false)
    private Integer billYear;

    @Column(name = "rent_amount", precision = 15, scale = 0, nullable = false)
    private BigDecimal rentAmount = BigDecimal.ZERO;

    @Column(name = "total_extra_fee", precision = 15, scale = 0, nullable = false)
    private BigDecimal totalExtraFee = BigDecimal.ZERO;

    /** Generated column ({@code rent_amount + total_extra_fee}) — read-only, DB computes it. */
    @Column(name = "total_amount", precision = 15, scale = 0, insertable = false, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 15, scale = 0, nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Generated column ({@code rent_amount + total_extra_fee - paid_amount}) — read-only. */
    @Column(name = "remaining_amount", precision = 15, scale = 0, insertable = false, updatable = false)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 30, nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.CHUA_THANH_TOAN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "monthlyBill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExtraFeeItem> extraFeeItems = new ArrayList<>();

    @OneToMany(mappedBy = "monthlyBill")
    private List<Payment> payments = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonthlyBill other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
