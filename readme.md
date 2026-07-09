# Diana Alejandra Erazo Pérez [00149823]

## Indicaciones

Recientemente, se utilizó AI para crear un sistema de gestion de una biblioteca, el cual ha generado varios errores, su trabajo es arreglarlo. Dado el siguiente caso de uso, explique y/o resuelva cada problema según se le pida.

---

## Consideraciones

La libreria crea automaticamente un correo con los nombres de la persona

---

## Problemas

### 1. Filtro por autor y género (10%)

QA ha reportado que el endpoint para obtener los libros puede filtrar por **autor** y por **género**, o por cualquiera de los dos de manera individual.

Actualmente:

- Filtrar únicamente por autor funciona correctamente.
- Filtrar únicamente por género funciona correctamente.
- Filtrar por **autor y género al mismo tiempo** provoca que el servidor falle.

**Instrucción:** Explique la causa del problema y resuélvalo.

La causa: es que el método findByAuthorAndGenre en BookRepository estaba declarado recibiendo el género como String, cuando el campo genre en la entidad Book es un enum (Genre). Además, en BookService.getAllBooks() los argumentos se pasaban en orden invertido: findByAuthorAndGenre(genre, author) en vez de findByAuthorAndGenre(author, genre). Esto provocaba que Hibernate intentara convertir el nombre del autor (ej. "Robert C. Martin") a un valor del enum Genre, lo cual no es posible y hacía fallar el servidor.
Solución: Se corrigió el tipo del parámetro a Genre en el repositorio, se arregló el orden de los argumentos en el servicio y se agregó .toUpperCase() para convertir el string recibido al formato del enum.

### 2. Error al volver a prestar un libro (10%)

Un usuario reportó que al pedir prestado el libro **The Selfish Gene**, devolverlo e intentar pedirlo prestado nuevamente, el servidor falla.

**Instrucción:** Explique la causa del problema y resuélvalo.

Causa: En MovementService, al devolver un libro (RETURN) el código incrementaba availableCount pero nunca volvía a poner available = true. Como "The Selfish Gene" solo tenía 1 copia, al prestarlo available pasaba a false; al devolverlo, aunque availableCount volvía a 1, el campo available se quedaba en false para siempre, bloqueando futuros préstamos.
Solución: Se agregó book.setAvailable(true) en la rama de devolución, restaurando correctamente la disponibilidad del libro.

### 3. Cantidad de libros por género (10%)

Existe un endpoint que devuelve la cantidad de libros disponibles por género. Sin embargo, actualmente dicho endpoint falla.

**Instrucción:** Explique la causa del problema y resuélvalo.

Causa: El libro "The Art of War" tiene genre = NULL en data.sql. El método getGenresAvailable() llamaba a book.getGenre().name() sin validar que genre no fuera nulo, provocando un NullPointerException al recorrer todos los libros.
Solución: Se agregó una validación (if (book.getGenre() == null) continue;) para omitir libros sin género definido al contar por categoría.

### 4. Error al consultar un libro por ID (10%)

Un miembro del equipo de frontend reporta que la siguiente llamada falla:

```http
GET /books?id=ed16ed1e-7017-4697-a08a-d28c09a74acf
```

**Instrucción:** Explique la causa del problema.

Causa: El endpoint para buscar un libro específico está definido como GET /books/{id} (id como path variable), no como GET /books?id=... (query param). El endpoint GET /books sí existe, pero solo reconoce los query params author y genre; cualquier otro parámetro (como id) simplemente se ignora. Por eso la llamada GET /books?id=... no "truena" con un error explícito, pero tampoco filtra nada: devuelve la lista completa de libros en vez del objeto único que el frontend espera, lo cual provoca el fallo del lado del cliente (ej. al intentar leer .title de un arreglo en vez de un objeto).
Corrección sugerida: El cliente (frontend) debe usar GET /books/{id} en lugar de GET /books?id=....

### 5. Error al crear un libro (10%)

QA ha reportado que el siguiente payload enviado al endpoint `POST /books` provoca un error:

```json
{
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "genre": "classic",
  "isbn": "978-0132350884",
  "available": true,
  "availableCount": 5

```

**Instrucción:** Explique la causa del problema.

Causa: BookService.createBook() convertía el género recibido con Genre.valueOf(dto.getGenre()) sin normalizar mayúsculas/minúsculas. El enum Genre solo acepta valores en mayúsculas (CLASSIC, NOVEL, etc.), así que al enviar "genre": "classic" (minúsculas) se lanzaba una IllegalArgumentException: No enum constant.
Solución: Se agregó .toUpperCase() antes de Genre.valueOf(), igual que ya se hacía en updateBook(), para aceptar el género sin importar mayúsculas/minúsculas.

### 6. Devolución de libros no prestados (20%)

QA ha reportado que un usuario es capaz de devolver libros que nunca ha solicitado en préstamo.

**Instrucción:**

- Confirme si este comportamiento es realmente posible.
- Si es posible, explique la causa y resuelva el problema.
- Si no es posible, explique por qué, haciendo referencia al código correspondiente.

Confirmación: Sí, el comportamiento era posible. MovementService.createMovement() solo validaba que el lector y el libro existieran, pero no verificaba que existiera un préstamo (BORROWING) activo y sin devolver antes de aceptar una RETURN. Esto permitía a cualquier lector "devolver" un libro que jamás pidió prestado, incrementando availableCount artificialmente e inflando el inventario.
Solución: Se agregó una consulta (findTopByLectorAndBookOrderByTimestampDesc) que busca el último movimiento entre ese lector y ese libro. Si no existe ningún movimiento previo, o el último no fue de tipo BORROWING (es decir, ya fue devuelto o nunca se prestó), la devolución se rechaza con un error 409 Conflict.