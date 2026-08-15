# إدارة الطفايات والمرتبات

تطبيق Android عربي لتسجيل:

- الموظفين والمرتبات والسلف داخل الشهر.
- العملاء وأرقامهم واللوكيشن وعدد الطفايات والسعر.
- نوع الطفاية ووزنها وعددها، مع تنبيه بعد 5 شهور ونصف لقرب انتهاء استيكر الطفاية.
- تقرير شهري بعدد الطفايات وإجمالي المبلغ ونسبة 25%.
- شهادات السلامة بتذكير سنوي قبل التاريخ بأسبوعين.
- التقارير الفنية بتذكير سنوي.
- عقود الصيانة بتذكير زيارة كل 3 شهور لمدة 5 سنوات، والتنبيه قبل الزيارة بـ 5 أيام، مع ترحيل الموعد لو وقع الجمعة أو السبت.
- تسجيل دخول Google ومزامنة Firebase Firestore.

## البناء على GitHub

1. ارفع محتويات هذا المجلد إلى Repository جديد.
2. من تبويب Actions شغل Workflow باسم `Build single APK`.
3. بعد انتهاء التشغيل حمل Artifact باسم `fire-salary-manager-single-apk`.
4. بداخله ستجد ملف واحد: `fire-salary-manager.apk`.

## ملاحظة Google Sync

تم تجهيز التطبيق بإعدادات Firebase المستخرجة من ملف APK الذي أرسلته وبنفس package name:

`com.mohamed.expenseguard`

لو تسجيل Google لم يعمل بعد تثبيت APK من GitHub، افتح Firebase Console للمشروع `masrofaty-ffde6` وأضف SHA-1 الذي يظهر في خطوة `Print debug SHA-1 for Firebase` داخل GitHub Actions، ثم حمل ملف `google-services.json` الجديد وضعه في:

`app/google-services.json`

بعدها شغل الـ Workflow مرة أخرى.
