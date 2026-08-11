/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Busqueda heredada de propietarios por apellido.
 */
@Controller
class OwnerSearchController {

	private final DataSource dataSource;

	OwnerSearchController(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@GetMapping("/owners/search-legacy")
	public @ResponseBody List<String> searchByLastName(@RequestParam String lastName) throws SQLException {
		List<String> names = new ArrayList<>();
		String sql = "SELECT first_name, last_name FROM owners WHERE last_name = '" + lastName + "'";
		try (Connection connection = this.dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				names.add(resultSet.getString("first_name") + " " + resultSet.getString("last_name"));
			}
		}
		return names;
	}

}
