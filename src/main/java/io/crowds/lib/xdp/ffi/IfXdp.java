package io.crowds.lib.xdp.ffi;

public interface IfXdp {
    /* Options for the sxdp_flags field */
    int XDP_SHARED_UMEM =	(1 << 0);
    int XDP_COPY =	(1 << 1);
    int XDP_ZEROCOPY =	(1 << 2);
    int XDP_USE_NEED_WAKEUP = (1 << 3);
    int XDP_USE_SG =	(1 << 4);

    /* Flags for xsk_umem_config flags */
    int XDP_UMEM_UNALIGNED_CHUNK_FLAG =	(1 << 0);
    int XDP_UMEM_TX_SW_CSUM =		(1 << 1);
    int XDP_UMEM_TX_METADATA_LEN =	(1 << 2);


    /* Flags for xsk_tx_metadata */
    int XDP_TXMD_FLAGS_TIMESTAMP = 1 << 0;
    int XDP_TXMD_FLAGS_CHECKSUM = 1 << 1;

    /* Options for xdp_desc */
    int XDP_PKT_CONTD = (1 << 0);
    int XDP_TX_METADATA = (1 << 1);

}
