package com.dataeater.app.ai

/** Reviewed public LiteRT-LM files. Pin revisions and hashes; never fetch arbitrary code. */
object ModelCatalog {
    data class Entry(
        val id: String, val name: String, val description: String,
        val repository: String, val revision: String, val fileName: String,
        val bytes: Long, val sha256: String, val license: String,
        val maxOutputTokens: Int = 512, val thinkingBudget: Int = 0, val cpuOnly: Boolean = false, val inputChars: Int = 6000, val inlineHistory: Boolean = false,
    ) {
        val url: String get() = "https://huggingface.co/$repository/resolve/$revision/$fileName"
        val sizeLabel: String get() = if (bytes < 1_000_000_000)
            "${bytes / 1_000_000} MB" else java.lang.String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
        fun fitsMemory(totalMb: Int): Boolean = bytes / 1_048_576 + 700 <= totalMb * 0.75
    }
    private val profiles = listOf(
        Entry("gemma4e2", "Gemma 4 · E2B", "Fast document assistant · recommended", "litert-community/gemma-4-E2B-it-litert-lm",
            "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1", "gemma-4-E2B-it-gpu.litertlm",
            2008432640L, "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5", "Apache-2.0"),
        Entry("qwen3508", "Qwen 3.5 · 0.8B", "Compact assistant", "litert-community/Qwen3.5-0.8B",
            "10243c0a01d24e6b122a0e814f8c4d984c9c92d6", "Qwen3.5-0.8B_int8.litertlm",
            963184864L, "64ed396fcdae75e5158945c77a08142b1322ce1bbf1be4d2198783119a1169e8", "Apache-2.0", maxOutputTokens = 384, inputChars = 5000, cpuOnly = true),
        Entry("qwen05", "Qwen 2 · 0.5B", "Small assistant", "litert-community/Qwen2-0.5B-Instruct",
            "13aab3e522828d85fa178d885716ab858a715149", "Qwen2_0.5B_Instruct.litertlm",
            647377840L, "0f01cc004b8eb62b92ba6be85ed05a248ba0d2f78af94c4949b313eccfb4c157", "Apache-2.0", maxOutputTokens = 256, inputChars = 4000, inlineHistory = true),
        Entry("smol036", "SmolLM 2 · 360M", "Very small · simple English chat", "litert-community/SmolLM2-360M-Instruct",
            "507c99cfe6541ba2bcd84818786f7b025935e5e1", "SmolLM2_360M_instruct.litertlm",
            373719040L, "8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e", "Apache-2.0", maxOutputTokens = 256, inputChars = 3000),
        Entry("qwen17", "Qwen 3 · 1.7B", "Smallest · simpler questions", "litert-community/Qwen3-1.7B",
            "73fbc3fe8271c162a603ee66f6e7ed25b6211195", "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            977184032L, "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b", "Apache-2.0", maxOutputTokens = 256, inputChars = 5000),
        Entry("lfm12", "LFM 2.5 · 1.2B", "Compact everyday assistant", "litert-community/LFM2.5-1.2B-Instruct",
            "a986969f90e60e694169b22e38b60a86e65c0448", "LFM2.5-1.2B-Instruct_int4_gpu.litertlm",
            736220768L, "97e6a9700208f59fce2ceb993cd6c18ea77ada8c2a940f68332004f38a404671", "LFM Open License v1.0"),
        Entry("qwen15", "Qwen 2.5 · 1.5B", "Multilingual chat and simpler documents", "litert-community/Qwen2.5-1.5B-Instruct",
            "19edb84c69a0212f29a6ef17ba0d6f278b6a1614", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            1597931520L, "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9", "Apache-2.0"),
        Entry("qwen4", "Qwen 3 · 4B", "Document assistant · larger download", "litert-community/Qwen3-4B-Instruct-2507",
            "4d409771b414d86b270ca54d27ffc45032a81142", "qwen3_4b_instruct_2507_mixed_int4.litertlm",
            2659057664L, "9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd", "Apache-2.0", inputChars = 4000),
        Entry("qwen06", "Qwen 3 · 0.6B", "Smallest · simple chat, limited with documents", "litert-community/Qwen3-0.6B-int4",
            "6aa2daf8aba4aa456797fb8040b36a3948bcfda7", "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            347251840L, "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139", "Apache-2.0", maxOutputTokens = 256, inputChars = 2400, inlineHistory = true),
        Entry("nemotron4", "Nemotron 3 Nano · 4B", "Slower reasoning assistant", "litert-community/Nemotron-3-Nano-4B",
            "77fb42cf0d0c7c8bab5d3e038960fd51d42ef880", "Nemotron-3-Nano-4B_int8.litertlm",
            4126697184L, "feefdcd55693022ddbdbed7d0aafdd266cac950c6a348d25a8e96b12827ddbda", "NVIDIA Nemotron Open Model License", 3200, 2048, true),
        Entry("lfm26", "LFM 2.5 · 2.6B", "Document questions and extraction", "litert-community/LFM2.5-2.6B",
            "b25501d2e9c6fd4e87cab606dc022f0fdf3d3970", "LFM2.5-2.6B_int4.litertlm",
            1668151680L, "d3e943dae301d88086c792b46786f26a60339998dac8fee34ed3f6cc4acccaac", "LFM Open License v1.0", 2048, 1024, false),
        Entry("phi4mini", "Phi-4 Mini Instruct", "Reasoning and document questions", "litert-community/Phi-4-mini-instruct",
            "8cd368be75fdb94d5a6f6f5b40f1ab22a6c2543e", "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            3910090752L, "7764d4deb53800578307be33039476b38a6c370fff71bedb3c0552563e23ab02", "MIT", 512, 0, false),
        Entry("qwen35", "Qwen 3.5 · 4B", "General assistant and comparison model", "litert-community/Qwen3.5-4B",
            "98d231a6c645d6865b0560088945560f7102958b", "Qwen3.5-4B_mixed_int4.litertlm",
            2754365536L, "50cbb13f782f4609ff11874ff15e79de5c1ee2e8856a21db72e7ef86f4acef35", "Apache-2.0", 512, 0, false),
        Entry("gemma4e4", "Gemma 4 · E4B", "General assistant · larger download", "litert-community/gemma-4-E4B-it-litert-lm",
            "2eee7ac325f20eb8c9ac1d0e972f7c84663062da", "gemma-4-E4B-it.litertlm",
            3659530240L, "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", "Apache-2.0", 512, 0, false),
    )
    // The simple download list only offers bundles measured in document tests.
    // Retain profiles for existing/manual files, including CPU-only Nemotron.
    val entries: List<Entry> get() = listOf("gemma4e2", "qwen17", "qwen15", "qwen4").mapNotNull { id -> profiles.find { it.id == id } }
    data class Unavailable(val name: String, val description: String)
    val unavailable: List<Unavailable> = emptyList()
    fun forFile(fileName: String): Entry? = profiles.find { it.fileName == fileName }
}

