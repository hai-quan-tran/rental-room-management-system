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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "checkout_checklist_item")
@Getter
@Setter
@NoArgsConstructor
public class CheckoutChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private CheckoutChecklist checklist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_handover_item_id", nullable = false)
    private RoomTypeHandoverItem roomTypeHandoverItem;

    /** Snapshot of the handover item's configured quantity at checkout time. */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "damaged_quantity", nullable = false)
    private Integer damagedQuantity = 0;

    @Column(name = "lost_quantity", nullable = false)
    private Integer lostQuantity = 0;

    @Column(name = "deduction_amount", precision = 15, scale = 0, nullable = false)
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "note", length = 300)
    private String note;

    /** Not persisted — derived from {@code totalQuantity - damagedQuantity - lostQuantity}. */
    public int getIntactQuantity() {
        return totalQuantity - damagedQuantity - lostQuantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CheckoutChecklistItem other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
