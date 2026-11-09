# Control de Pasajeros — proyecto Android

Este proyecto empaqueta el HTML original dentro de una aplicación Android (WebView) para uso local y sin conexión.

Versión actual: **1.6** (`versionCode 7`, paquete `com.controlpasajeros.app`).

## Características incluidas
- Aplicación Android con nombre `Control de Pasajeros`.
- El HTML se carga desde los recursos internos; no necesita Internet.
- JavaScript y DOM storage habilitados para que `localStorage` conserve los datos entre aperturas.
- Compartir manifiestos mediante el selector nativo de Android.
- Envío del informe al sistema de impresión de Android para guardar como PDF o imprimir.
- Flujo de GitHub Actions para compilar una APK de depuración y publicarla como artefacto descargable.

## Novedades de la v1.6
- **Imagen del reporte COMPLETA (arreglo del bug de la v1.5):** en la v1.5 la imagen PNG/JPG salía solo con la cabecera, sin ningún pasajero: se generaba capturando un WebView invisible con `view.draw()`, que solo rasteriza lo que cabe en su viewport y nunca dibujaba la tabla. Ahora la imagen se dibuja de forma determinista en un `<canvas>` de JavaScript (cabecera, zonas y TODAS las filas, con la altura calculada exactamente desde el número de zonas y filas, y el pie dentro de la imagen), se pasa como base64 al puente Android (`shareImageData`) y Android solo la guarda en `Imágenes/ControlPasajeros` y la comparte. En navegador sin puente se descarga la misma imagen del canvas. La captura por WebView ya no se usa. Verificado con manifiestos largos (60 pasajeros en 3 zonas y 100 en 1 zona): todas las filas y el pie quedan dentro de la imagen.
- **La v1.6 SÍ se instala encima de la v1.4 y la v1.5** (misma llave debug, huella SHA-256 `E7:46:6A:CF:…:2F`), sin desinstalar y sin perder datos. Aun así, haz un respaldo JSON antes de actualizar, por precaución.

## Novedades de la v1.5
- **Asientos disponibles en vivo:** al añadir o editar un pasajero, debajo del campo de asientos aparece la zona "Asientos disponibles" con los números libres como botones tocables: al pulsar uno se añade al campo, sin duplicar y sin pasar la cantidad pedida. Si el viaje tiene capacidad, muestra "Quedan X de Y libres" (si no queda ninguno, avisa "No quedan asientos libres"); si no tiene capacidad, muestra los 10 primeros libres con la nota "Sin capacidad definida". Al editar, los asientos del propio pasajero no se cuentan como ocupados. En modo "solo cantidad" la zona se oculta. En "Resumen del viaje" también hay una línea compacta "Libres: X/Y". Los asientos no numéricos (p. ej. "A1") siguen contando como ocupados y no aparecen como disponibles; la validación actual (cantidad, repetidos, ocupados) no cambia.

## Novedades de la v1.4
- **Clonar al crear viaje:** en el modal de crear viaje se puede elegir "Copiar de un viaje existente", que precarga zonas, modo, capacidad y título del reporte; con la casilla "Copiar también los pasajeros" se copian además los pasajeros (con IDs nuevos y zonas remapeadas). El botón ⧉ de Historial abre ese mismo modal con el origen ya elegido.
- **Clonar pasajero:** botón ⧉ en cada tarjeta de pasajero; abre el formulario prellenado con sus datos, ID nuevo y asientos vaciados para asignarlos sin choques.
- **Contacto desde el número:** en cada pasajero con teléfono, botones WhatsApp, SMS y Llamar. El número cubano se normaliza solo (8 dígitos con 5 inicial → +53; si ya trae 53 o +53 se respeta), sin añadir el contacto. Los enlaces `tel:`, `sms:` y `wa.me` se abren fuera del WebView con la app correspondiente.
- **Título del reporte editable:** cada viaje tiene su `reportTitle` (por defecto "MANIFIESTO DE PASAJEROS"), editable en "Resumen del viaje" y en el modal de crear/clonar viaje. Se usa en la vista previa, en el texto plano, en el título de impresión/PDF y en la imagen del reporte. Los viajes viejos sin ese campo usan el valor por defecto.
- **Respaldo y restauración:** en Historial, "Crear respaldo" exporta todos los datos a `Descargas/ControlPasajeros/respaldo-control-pasajeros-AAAA-MM-DD.json` y "Restaurar respaldo" importa un archivo `.json` (valida el formato y pide confirmación, porque reemplaza los datos actuales). Sirve para pasar los datos a otro teléfono con la misma APK. En navegador sin puente funciona con descarga de blob y FileReader.
- **Asiento opcional:** en modo numerado el número de asiento ya no es obligatorio al añadir un pasajero; se puede dejar vacío y asignarlo después al editarlo. Si está vacío, la lista y el reporte muestran "Sin asignar". Si se escriben números, se mantiene toda la validación (cantidad coincidente, sin repetidos, sin asientos ocupados). El modo "solo cantidad" no cambia.
- **Compartir como imagen:** en la vista Reporte y en el modal de compartir, botones "Imagen PNG" e "Imagen JPG". (En la v1.4/v1.5 la imagen se generaba capturando un WebView y salía incompleta — solo la cabecera —; la v1.6 la dibuja completa en canvas, ver arriba.) PDF, compartir texto, copiar y .txt siguen como antes.
- **Interfaz azul fuerte:** cabecera con degradado azul profundo, botones primarios azul intenso, títulos de sección en azul oscuro, nombre del viaje y del pasajero grandes y en negrita, etiquetas de campo en mayúsculas, estadísticas grandes en azul y texto principal casi negro-azulado. Mismo layout, mismos botones y mismos textos; solo color, peso, tamaño y espaciado. El PDF/manifiesto impreso mantiene el azul fuerte en el título.

## Cómo obtener la APK mediante GitHub Actions
1. Crea un repositorio de GitHub y sube todo el contenido de esta carpeta a la rama `main`.
2. Abre la pestaña **Actions** del repositorio y habilita los flujos si GitHub lo solicita.
3. Ejecuta **Build Android APK** (o espera a que termine el flujo activado por el `push`).
4. En la ejecución completada, descarga el artefacto **Control-de-Pasajeros-APK**.
5. Descomprime el artefacto y copia `app-debug.apk` al teléfono Android para instalarlo. Puede que Android pida autorizar la instalación desde esa fuente.

## Instalación / actualización — ¡LEER ANTES DE INSTALAR LA v1.4!
- La v1.4 **no se puede instalar encima** de la v1.0–v1.3: la llave debug con la que se firmaron esas versiones se perdió en un reemplazo de la máquina de compilación, y la v1.4 se firmó con una llave nueva. Android rechazará la actualización si intentas instalarla encima.
- **Si tienes datos en la app instalada y no quieres perderlos, haz primero una copia con adb desde tu PC** (la app permite copia de seguridad, `android:allowBackup="true"`):
  1. Conecta el teléfono por USB con depuración USB activada.
  2. `adb backup -f control-pasajeros.ab com.controlpasajeros.app` (acepta la copia en el teléfono).
  3. Desinstala la app vieja, instala la v1.4.
  4. `adb restore control-pasajeros.ab` para devolver los datos.
- Si no tienes datos que conservar, desinstala la versión vieja e instala la v1.4 directamente.
- **A partir de la v1.4 esto ya no vuelve a pasar:** la llave de firma está respaldada y las próximas versiones se firmarán con la misma, así que se instalarán encima sin perder datos. Además la propia app ya trae "Crear respaldo" en Historial: haz un respaldo antes de cada actualización.
- **La v1.5 SÍ se instala encima de la v1.4** (misma llave debug, huella SHA-256 `E7:46:6A:CF:…:2F`), sin desinstalar y sin perder datos. Aun así, haz un respaldo JSON antes de actualizar, por precaución.

## Datos y copias de seguridad
Los datos quedan en el almacenamiento local del WebView (`localStorage`, clave `controlPasajeros.v1`). Como en cualquier app local, desinstalar o borrar los datos de la app puede eliminarlos. Conserva una copia independiente de los datos importantes. Desde la v1.4, usa "Crear respaldo" en Historial para exportar todo a un JSON.

## Firma (para quien compile)
- La llave debug de la v1.4 está respaldada fuera del repo en `~/workspace/keys/control-pasajeros-debug.keystore` (alias `androiddebugkey`, contraseña `android`, huella SHA-256 `E7:46:6A:CF:81:3F:6E:61:9B:EB:2C:2B:C6:FC:C2:FD:58:91:75:1A:7F:0E:BC:46:DC:73:49:2F:02:D1:9F:2F`).
- Antes de compilar una versión futura en una máquina nueva, restaura esa llave como `~/.android/debug.keystore` (o `/root/.android/debug.keystore`, según el usuario que compile) para que la APK se firme con la misma llave y se instale encima de la v1.4 sin perder datos.
- **No subas el keystore al repositorio.**

## Estado
Este paquete contiene el proyecto fuente y la automatización de compilación. La APK no está precompilada dentro de este ZIP. La compilación debe ejecutarse en un entorno con Android SDK, como GitHub Actions o Android Studio.
