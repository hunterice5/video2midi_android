# Video2Midi Android (Optimized Edition)

Android application to convert YouTube Synthesia-style piano videos to MIDI files.

* Original PC project: [https://github.com/svsdval/video2midi](https://github.com/svsdval/video2midi)
* Original Android project credit: [https://github.com/svsdval/video2midi_android](https://github.com/svsdval/video2midi_android) by @svsdval

---

## 🚀 Key Optimizations & Fixes (รายการปรับปรุงและแก้ไข)

This version resolves the critical performance bottleneck and MIDI output stuttering issues found in the original codebase:

### 1. ⚡ Hardware-Accelerated Sequential Decoding (ระบบถอดรหัสความเร็วสูง)
* **English:** Re-enabled and corrected the hardware-accelerated `SequentialDecoder` (`MediaCodec`). Fixed a critical frame index drift where frames were incorrectly mapped due to B-frame reordering and keyframe seek offsets. Now, the frame index is calculated dynamically and accurately using the presentation timestamp (`bufferInfo.presentationTimeUs` and `fps`).
  * **Performance Impact:** Speed improved **30x–40x**! A 3.5-minute video that previously took **30 minutes** to convert now completes in **under 1 minute**.
* **ไทย:** เปิดใช้งานตัวถอดรหัสฮาร์ดแวร์ความเร็วสูง `SequentialDecoder` (`MediaCodec`) และแก้ไขข้อผิดพลาดเรื่องตำแหน่งเฟรมคลาดเคลื่อน (Timestamp Drift) โดยการคำนวณหมายเลขเฟรมจริงจากเวลาแสดงผลของวิดีโอ (Presentation Timestamp) ทำให้แปลงวิดีโอความยาว 3.5 นาทีจากที่เคยใช้เวลา **30 นาที เหลือไม่ถึง 1 นาที** โดยไม่มีอาการเวลาของโน้ตคลาดเคลื่อน

### 2. 🎵 MIDI Audio Stuttering Resolution (แก้ปัญหาเสียงสะดุด)
* **English:** Implemented `mergeOverlappingNotes()` in `MidiGenerator.java` to automatically merge overlapping or rapidly repeating notes of the same pitch within an 80ms window. This eliminates the synthesizer stuttering caused by color detection pixel flicker.
* **ไทย:** เพิ่มระบบรวมโน้ตที่ซ้ำซ้อนซ้อนทับกันอันเกิดจากการกะพริบของสีพิกเซล (Flicker) บนหน้าจอหากช่องว่างน้อยกว่า 80ms ช่วยให้ไฟล์ MIDI ที่ได้มีเสียงที่ลื่นไหลขึ้นและหมดปัญหาเสียงสะดุดหรือสะท้อนซ้อนกัน

### 3. 🖼️ Codec-Specific Auto-Routing (สลับเครื่องมือดึงเฟรมอัตโนมัติ)
* **English:** Added smart codec MIME type detection. H.264/HEVC/MP4 videos are routed to `FFmpegMediaMetadataRetriever` (ensuring 100% frame accuracy), while AV1 encoded videos (`video/av01`) are automatically routed to the native media retriever utilizing API 28+ `getFrameAtIndex()` to prevent retrieval failure loops.
* **ไทย:** เพิ่มการตรวจสอบประเภท Codec ของวิดีโอ หากเป็น H.264/HEVC จะเลือกใช้ FFmpeg ในการดึงเฟรมอย่างแม่นยำ ส่วนวิดีโอประเภท AV1 จะสลับไปใช้ Android Native API 28+ `getFrameAtIndex()` ทันทีเพื่อป้องกันปัญหา Failed to load frame

### 4. 🏎️ Pixel Retrieval JNI Optimization (ลดภาระ JNI ในการประมวลผลพิกเซล)
* **English:** Replaced JNI-heavy individual `Bitmap.getPixel` queries in the keyboard scanning loop (88+ times per frame) with a single pre-allocated `getPixels()` bulk buffer memory copy per frame.
* **ไทย:** ปรับปรุงความเร็วในการอ่านค่าสีของคีย์บอร์ด โดยเปลี่ยนจากการเรียก JNI `Bitmap.getPixel` ทีละปุ่ม (88+ ครั้งต่อเฟรม) มาเป็นดึงค่าสีทั้งหมดลงหน่วยความจำบัฟเฟอร์ในรอบเดียว ช่วยลดภาระการประมวลผลต่อเฟรมลงอย่างมาก
