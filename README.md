# Hey SHAVi 🎙️

Ek Kotlin-based Android voice assistant jo "Hey SHAVi" bolne se activate hoti hai aur Gemini API ka use karke commands samajhti hai — call karna, time batana, aur aage extend karne par apps kholna / message bhejna / scroll karna.

Is guide mein sab kuch **sirf phone se** karne ka tareeka hai — koi computer/laptop nahi chahiye.

---

## Step 1: Gemini API Key lein
1. Phone browser mein https://aistudio.google.com/apikey kholein
2. Google account se login karein
3. "Create API Key" dabayein aur key copy kar lein (sambhal ke rakhein, kisi ko na dikhayein)

---

## Step 2: GitHub par code upload karein (APK build karne ke liye)
1. Phone par **GitHub app** install karein (Play Store se) ya browser use karein
2. https://github.com par free account banayein (agar nahi hai)
3. Ek naya **public ya private repository** banayein, naam dein jaise `HeySHAVi`
4. Is poore project folder (jo maine banaya hai) ke saare files us repo mein upload karein:
   - GitHub app mein repo kholkar "Add file" → "Upload files" se sab files/folders daal sakte hain
   - Ya phone browser se github.com par jaake drag-drop se upload karein
   - **Zaroori:** poora folder structure waisa hi rakhein jaisa hai (`.github/workflows/build.yml` bhi zaroor upload karein)

---

## Step 3: Automatic build chalayein
1. Jaise hi files `main` branch par upload hoti hain, GitHub Actions apne aap build shuru kar dega
2. Repo ke **"Actions"** tab mein jaake dekhein — "Build SHAVi APK" workflow chal raha hoga (2-4 minute lagte hain)
3. Build complete hone ke baad, us workflow run ko kholein → neeche **"Artifacts"** section mein `HeySHAVi-debug-apk` milega
4. Usko download kar lein — ye ek `.zip` hoga, usme se `app-debug.apk` nikaal lein

---

## Step 4: APK install karein
1. Phone Settings → Security/Privacy mein "Install unknown apps" allow karein (browser/Files app ke liye)
2. Downloaded `app-debug.apk` par tap karke install karein
3. App kholein — naam "Hey SHAVi" dikhega

---

## Step 5: App set up karein
1. App khulte hi apni **Gemini API key paste karein** aur "Save Karein" dabayein
2. "Permissions Allow Karein" dabakar Microphone, Call, Contacts, SMS permissions allow karein
3. (Optional, scroll feature ke liye) Settings → Accessibility mein jaakar "Hey SHAVi" ko on karein
4. "SHAVi Start Karein" dabayein — assistant background mein chalne lagega

---

## Use kaise karein
Bolein: **"Hey SHAVi"** → wo "Ji bataiye" bolegi → phir apna command bolein:
- "Mummy ko call karo"
- "Time kya hua hai?"
- "WhatsApp kholo"
- "Papa ko message bhejo ki main aa raha hoon" *(agar SMS permission hai)*
- "Neeche scroll karo" *(Accessibility on hone par)*

---

## Abhi kya kaam karta hai vs kya aage badhana hoga
✅ Poori tarah kaam karta hai: **Call karna**, **Time batana**, App kholna, SMS bhejna (basic)
🔧 Extend karna hoga: Scroll (Accessibility service ka base already bana hai, gestures customize kar sakte hain), better wake-word accuracy (abhi Android ka built-in SpeechRecognizer use ho raha hai — battery-heavy hai; production ke liye Picovoice Porcupine jaisa dedicated wake-word engine better rahega)

## Important note
Android ka SpeechRecognizer background mein continuously chalne ke liye designed nahi hai — kabhi kabhi restart hone mein delay ho sakta hai ya battery zyada use ho sakti hai. Ye demo/personal-use ke liye perfect hai, lekin Play Store par publish karne ke liye Google ki Accessibility/background-mic policies dhyan se padhni hongi.
