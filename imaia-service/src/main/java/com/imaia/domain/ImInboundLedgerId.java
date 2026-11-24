package com.imaia.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite key of {@link ImInboundLedger}. */
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ImInboundLedgerId implements Serializable {

    private Long accountId;
    private String platformMessageId;
    private String eventKind;
}
