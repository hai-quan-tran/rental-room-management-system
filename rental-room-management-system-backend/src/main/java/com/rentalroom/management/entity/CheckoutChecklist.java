package com.rentalroom.management.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** At most one per contract (a contract only ever ends/checks out once) — {@code contract_id} is unique. */
@Entity
@Table(name = "checkout_checklist")
@Getter
@Setter
@NoArgsConstructor
public class CheckoutChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private Contract contract;

    /** Audit metadata only — the acting account id, not a managed association. */
    @Column(name = "checked_by")
    private Long checkedBy;

    @CreationTimestamp
    @Column(name = "checked_at", nullable = false, updatable = false)
    private LocalDateTime checkedAt;

    /** Snapshot of {@code contract.deposit_amount} at checkout time. */
    @Column(name = "deposit_amount", precision = 15, scale = 0, nullable = false)
    private BigDecimal depositAmount;

    @Column(name = "deduction_amount", precision = 15, scale = 0, nullable = false)
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "deposit_refund_amount", precision = 15, scale = 0, nullable = false)
    private BigDecimal depositRefundAmount = BigDecimal.ZERO;

    @Column(name = "note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CheckoutChecklistItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "checklist")
    private List<DebtRecord> debtRecords = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CheckoutChecklist other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
