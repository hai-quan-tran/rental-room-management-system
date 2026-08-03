package com.rentalroom.management.entity;

import com.rentalroom.management.enums.ContractStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * "At most 1 ACTIVE contract per room" is enforced in the DB by triggers
 * ({@code trg_contract_before_insert}/{@code trg_contract_before_update}, which raise
 * SQLSTATE 45000 on violation) rather than a unique constraint — a generated-column +
 * unique-index approach hit MySQL 8/InnoDB bug 1215 (STORED column depending on a PK/FK
 * column). Callers should expect a generic SQL exception (not
 * {@code DataIntegrityViolationException}) on violation until the service layer adds
 * pre-validation.
 */
@Entity
@Table(name = "contract")
@Getter
@Setter
@NoArgsConstructor
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "monthly_rent", precision = 15, scale = 0, nullable = false)
    private BigDecimal monthlyRent;

    @Column(name = "deposit_amount", precision = 15, scale = 0, nullable = false)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ContractStatus status = ContractStatus.ACTIVE;

    /** Audit metadata only — the acting account id, not a managed association. */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContractTenant> contractTenants = new ArrayList<>();

    @OneToMany(mappedBy = "contract")
    private List<MonthlyBill> monthlyBills = new ArrayList<>();

    @OneToMany(mappedBy = "contract")
    private List<DebtRecord> debtRecords = new ArrayList<>();

    @OneToOne(mappedBy = "contract")
    private CheckoutChecklist checkoutChecklist;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Contract other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
