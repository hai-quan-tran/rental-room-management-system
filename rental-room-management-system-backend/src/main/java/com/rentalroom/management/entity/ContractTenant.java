package com.rentalroom.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * N-N join between {@link Contract} and {@link Tenant}. {@code is_representative} is the sole
 * source of truth for "người đại diện ký hợp đồng" — enforced to exactly one per contract by
 * DB triggers ({@code trg_ct_before_insert}/{@code trg_ct_before_update}, which raise SQLSTATE
 * 45000 on violation), not a unique constraint — a generated-column + unique-index approach hit
 * MySQL 8/InnoDB bug 1215 (STORED column depending on a PK/FK column).
 */
@Entity
@Table(name = "contract_tenant")
@Getter
@Setter
@NoArgsConstructor
public class ContractTenant {

    @EmbeddedId
    private ContractTenantId id = new ContractTenantId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contractId")
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tenantId")
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "is_representative", nullable = false)
    private boolean representative = false;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContractTenant other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
