package br.com.pausaativa.treino.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExercicioJpa extends JpaRepository<ExercicioEntity, String> {}
