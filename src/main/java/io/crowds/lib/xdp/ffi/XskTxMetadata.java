package io.crowds.lib.xdp.ffi;

import top.dreamlike.panama.generator.annotation.Union;

//struct xsk_tx_metadata {
//	__u64 flags;
//
//	union {
//		struct {
//			/* XDP_TXMD_FLAGS_CHECKSUM */
//
//			/* Offset from desc->addr where checksumming should start. */
//			__u16 csum_start;
//			/* Offset from csum_start where checksum should be stored. */
//			__u16 csum_offset;
//		} request;
//
//		struct {
//			/* XDP_TXMD_FLAGS_TIMESTAMP */
//			__u64 tx_timestamp;
//		} completion;
//	};
//};
public class XskTxMetadata {

    private long flags;
    private UnionStruct union;

    @Union
    public static class UnionStruct {
        private Request request;
        private Completion completion;

        public Request getRequest() {
            return request;
        }

        public void setRequest(Request request) {
            this.request = request;
        }

        public Completion getCompletion() {
            return completion;
        }

        public void setCompletion(Completion completion) {
            this.completion = completion;
        }
    }

    public static class Request{
        private short csum_start;
        private short csum_offset;

        public short getCsum_start() {
            return csum_start;
        }

        public void setCsum_start(short csum_start) {
            this.csum_start = csum_start;
        }

        public short getCsum_offset() {
            return csum_offset;
        }

        public void setCsum_offset(short csum_offset) {
            this.csum_offset = csum_offset;
        }
    }

    public static class Completion{
        private long tx_timestamp;

        public long getTx_timestamp() {
            return tx_timestamp;
        }

        public void setTx_timestamp(long tx_timestamp) {
            this.tx_timestamp = tx_timestamp;
        }
    }

    public long getFlags() {
        return flags;
    }

    public void setFlags(long flags) {
        this.flags = flags;
    }

    public UnionStruct getUnion() {
        return union;
    }

    public void setUnion(UnionStruct union) {
        this.union = union;
    }
}
