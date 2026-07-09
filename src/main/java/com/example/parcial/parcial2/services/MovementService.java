package com.example.parcial.parcial2.services;

import com.example.parcial.parcial2.domain.dtos.MovementRequestDto;
import com.example.parcial.parcial2.domain.entities.Book;
import com.example.parcial.parcial2.domain.entities.Lector;
import com.example.parcial.parcial2.domain.entities.Movement;
import com.example.parcial.parcial2.domain.entities.MovementType;
import com.example.parcial.parcial2.exception.BusinessException;
import com.example.parcial.parcial2.exception.ResourceNotFoundException;
import com.example.parcial.parcial2.repositories.BookRepository;
import com.example.parcial.parcial2.repositories.LectorRepository;
import com.example.parcial.parcial2.repositories.MovementRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class MovementService {

    private final MovementRepository movementRepository;
    private final LectorRepository lectorRepository;
    private final BookRepository bookRepository;

    public MovementService(MovementRepository movementRepository,
                           LectorRepository lectorRepository,
                           BookRepository bookRepository) {
        this.movementRepository = movementRepository;
        this.lectorRepository = lectorRepository;
        this.bookRepository = bookRepository;
    }

    public Movement borrowBook(MovementRequestDto dto) {
        return createMovement(dto, MovementType.BORROWING);
    }

    public Movement returnBook(MovementRequestDto dto) {
        return createMovement(dto, MovementType.RETURN);
    }

    private Movement createMovement(MovementRequestDto dto, MovementType type) {
        Lector lector = lectorRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Lector not found"));

        Book book = bookRepository.findByIsbn(dto.getIsbn())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        if (type == MovementType.BORROWING) {
            if (!book.isAvailable() || book.getAvailableCount() <= 0) {
                throw new BusinessException("Book is not available");
            }
            book.setAvailableCount(book.getAvailableCount() - 1);
            if (book.getAvailableCount() == 0) {
                book.setAvailable(false);
            }
        } else {
            // Verificamos que exista un préstamo activo (sin devolver) de este
            // libro por este lector antes de aceptar la devolución.
            Optional<Movement> lastMovement =
                    movementRepository.findTopByLectorAndBookOrderByTimestampDesc(lector, book);

            if (lastMovement.isEmpty() || lastMovement.get().getType() != MovementType.BORROWING) {
                throw new BusinessException("This lector does not have this book currently borrowed");
            }

            book.setAvailableCount(book.getAvailableCount() + 1);
            book.setAvailable(true);
        }

        bookRepository.save(book);

        Movement movement = new Movement();
        movement.setLector(lector);
        movement.setBook(book);
        movement.setTimestamp(Instant.now());
        movement.setType(type);

        return movementRepository.save(movement);
    }
}
