package com.draft.footy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persists in-progress and completed draft runs, keyed by the run's UUID id (DESIGN_SPEC §16). */
public interface DraftRunRepository extends JpaRepository<DraftRunEntity, String> { }
