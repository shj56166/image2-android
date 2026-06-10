# Image2生圖 Android

[English](../README.md) | [简体中文](README-zh_Hans.md) | 繁體中文

Android 生圖應用，適用於 GPT Image 與相容中轉 API。

[下載發布版](https://github.com/shj56166/image2-android/releases)

<p>
  <img src="../docs/images/create.jpg" width="260" alt="建立頁" />
  <img src="../docs/images/parameters.jpg" width="260" alt="參數頁" />
</p>

## 功能

- 文字生圖與參考圖生圖
- 尺寸、品質、格式、壓縮、審核、數量參數
- 任務歷史與結果預覽
- API 設定儲存在本機
- 简体中文、繁體中文、English

## 建置

需求：

- JDK 17
- Android SDK API 35

```powershell
.\gradlew.bat clean test assembleRelease
```

套件名稱：

```text
com.shj56166androidimage2.app
```

## 技術棧

- Kotlin 與 Gradle Kotlin DSL
- Jetpack Compose 與 Material 3
- AndroidX Lifecycle、DataStore、Security Crypto、WorkManager
- Room 資料庫與 KSP
- OkHttp、Retrofit、kotlinx.serialization
- Coil 圖片載入

## 主要架構

- `ui`：Compose 頁面、應用根元件、主題、ViewModel
- `data`：Room 實體與 DAO、Repository、網路模型、服務商映射、本機儲存
- `domain`：生圖執行引擎介面
- `worker`：背景生圖任務執行
- `util`：應用語言與區域設定輔助

應用在裝置本機保存 API 設定、任務記錄、會話、草稿和生成圖片中繼資料。網路請求由生圖執行引擎統一處理，支援 OpenAI 相容的 `/responses`、`/images/generations`、`/images/edits`，fal.ai 風格介面，以及自訂服務商映射。

## 開源協議

MIT

## 借鑑

Inspired by [CookSleep/gpt_image_playground](https://github.com/CookSleep/gpt_image_playground).
