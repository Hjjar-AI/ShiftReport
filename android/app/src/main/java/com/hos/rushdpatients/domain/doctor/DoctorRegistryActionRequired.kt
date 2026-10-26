package com.hos.rushdpatients.domain.doctor

enum class DoctorRegistryAction { FETCH, PUBLISH }

/** A specific next action for registry state, without publishing or replacing it implicitly. */
class DoctorRegistryActionRequired(val action: DoctorRegistryAction, message: String) : IllegalStateException(message)
